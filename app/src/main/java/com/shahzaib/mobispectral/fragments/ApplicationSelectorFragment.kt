package com.shahzaib.mobispectral.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.Intent.CATEGORY_OPENABLE
import android.database.Cursor
import android.graphics.ImageFormat
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.Navigation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shahzaib.mobispectral.MainActivity
import com.shahzaib.mobispectral.R
import com.shahzaib.mobispectral.Utils
import com.shahzaib.mobispectral.compressImage
import com.shahzaib.mobispectral.databinding.FragmentApplicationselectorBinding
import com.shahzaib.mobispectral.makeDirectory
import com.shahzaib.mobispectral.readImage
import com.shahzaib.mobispectral.saveImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class ApplicationSelectorFragment: Fragment() {
    private lateinit var fragmentApplicationselectorBinding: FragmentApplicationselectorBinding
    private lateinit var applicationArray: Array<String>

    private fun getRealPathFromURI(contentUri: Uri): String {
        val proj = arrayOf(MediaStore.Images.Media.DATA)
        val cursor: Cursor = requireContext().contentResolver.query(contentUri, proj, null, null, null)!!
        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        cursor.moveToFirst()
        val absolutePath = cursor.getString(columnIndex)
        cursor.close()
        return absolutePath
    }

    fun generateAlertBox(context: Context, title: String, text: String) {

        val alertDialogBuilder = MaterialAlertDialogBuilder(context, R.style.AlertDialogTheme)
        alertDialogBuilder.setMessage(text)
        alertDialogBuilder.setTitle(title)
        alertDialogBuilder.setCancelable(false)
        if (title == "Information")
            alertDialogBuilder.setPositiveButton("Okay") { dialog, _ -> dialog?.cancel() }
        else
            alertDialogBuilder.setPositiveButton("Reload") { _, _ ->
                startMyActivityForResult()
            }

        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }

    private val myActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->



        if (result.resultCode == Activity.RESULT_OK) {

            if (result.data?.clipData == null) {

                val cameraIdNIR = Utils.getCameraIDs(requireContext(), MainActivity.MOBISPECTRAL_APPLICATION).second

                if (cameraIdNIR == "OnePlus")
                {
                    // If we have to select one image.
                    val nirUri: Uri? = result.data!!.data
                    CameraFragment.nirAbsolutePath = nirUri?.let { getRealPathFromURI(it) }.toString()
                    Log.i("Images Opened Path", "NIR Path: ${CameraFragment.nirAbsolutePath}")
                }
            }
            else {

                if (result.data?.clipData?.itemCount == 2) {
                    var rgbFile = getRealPathFromURI(result.data!!.clipData?.getItemAt(0)?.uri!!)
                    var nirFile = getRealPathFromURI(result.data!!.clipData?.getItemAt(1)?.uri!!)

                    if (rgbFile.contains("NIR")) {
                        val tempFile = rgbFile
                        rgbFile = nirFile
                        nirFile = tempFile
                    }

                    var rgbBitmap = readImage(rgbFile)
                    var nirBitmap = readImage(nirFile)

                    MainActivity.originalImageRGB = rgbFile
                    MainActivity.originalImageNIR = nirFile

                    if (nirBitmap.width > rgbBitmap.width && nirBitmap.height > rgbBitmap.height) {
                        val tempBitmap = rgbBitmap
                        rgbBitmap = nirBitmap
                        nirBitmap = tempBitmap
                    }

                    rgbBitmap = compressImage(rgbBitmap)
                    nirBitmap = compressImage(nirBitmap)

                    val rgbBitmapOutputFile = CameraFragment.createFile("RGB")
                    val nirBitmapOutputFile = File(rgbBitmapOutputFile.toString().replace("RGB", "NIR"))
                    CameraFragment.rgbAbsolutePath = rgbBitmapOutputFile.absolutePath
                    CameraFragment.nirAbsolutePath = nirBitmapOutputFile.absolutePath
                    Log.i("Images Opened Path", "RGB Path: ${CameraFragment.rgbAbsolutePath}, NIR Path: ${CameraFragment.nirAbsolutePath}")

                    lifecycleScope.launch {
                        saveImage(rgbBitmap, rgbBitmapOutputFile)
                        saveImage(nirBitmap, nirBitmapOutputFile)

                        withStarted {
                            navController.safeNavigate(
                                    ApplicationSelectorFragmentDirections.actionAppselectorToJpegViewer(
                                            CameraFragment.rgbAbsolutePath, CameraFragment.nirAbsolutePath
                                    )
                            )
                        }

                    }
                }
                else if (result.data?.clipData?.itemCount == 1)
                {
                    generateAlertBox(requireContext(), "Only One Image Selected", "Cannot select 1 image, Select Two images.\nFirst image RGB, Second image NIR")
                }
                else {
                    generateAlertBox(requireContext(),"Number of images exceeded 2", "Cannot select more than 2 images.\nFirst image RGB, Second image NIR")
                }
            }
        }
        if (result.resultCode == Activity.RESULT_CANCELED) {
            generateAlertBox(requireContext(), "No Images Selected", "Select Images again.\nFirst image RGB, Second image NIR")
        }
    }

    private fun startMyActivityForResult() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        lifecycleScope.launch(Dispatchers.Main) {
            myActivityResultLauncher.launch(galleryIntent)
        }
    }

    private val filesActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result ->
        if (result.resultCode == Activity.RESULT_OK)
        {
            // List to contain all selected Uris
            val uriList: MutableList<Uri> = mutableListOf()

            // ContentResolver for determining the type of data from the Uris
            val resolver = context?.contentResolver

            // Check if only one item is selected
            result.data?.data?.let {
                uriList.add(it)
            }   // multiple items selected?
                ?: result.data?.clipData?.let {
                Log.i("FILES_BTN", "${it.itemCount} items have been selected")

                for (index in 0 until it.itemCount)
                {
                    val uri = it.getItemAt(index).uri
                    uriList.add(uri)
                }
            }

            Log.i("FILES_BTN", "All selected files: $uriList")

            val imageList: MutableList<Uri> = mutableListOf()
            uriList.forEach{
                // check if the file is of type image/jpeg, image/png, ...
                if (resolver?.getType(it)?.subSequence(0,5)?.equals("image") == true)
                    imageList.add(it)
            }

            Log.i("FILES_BTN", "Images: $imageList")

        }

        if (result.resultCode == Activity.RESULT_CANCELED)
        {
            generateAlertBox(requireContext(), "No Files Selected", "Please try again")
        }
    }

    private fun startFilesActivityForResult() {
        val filesIntent = Intent(Intent.ACTION_GET_CONTENT)
        filesIntent.type = "*/*"
        filesIntent.addCategory(CATEGORY_OPENABLE)
        filesIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

        lifecycleScope.launch(Dispatchers.Main) {
            filesActivityResultLauncher.launch(filesIntent)
        }
    }

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
    }

    private fun NavController.safeNavigate(direction: NavDirections) {
        currentDestination?.getAction(direction.actionId)?.run {
            navigate(direction)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentApplicationselectorBinding = FragmentApplicationselectorBinding.inflate(inflater, container, false)
        val applicationPicker = fragmentApplicationselectorBinding.applicationPicker
        applicationArray = arrayOf(getString(R.string.apple_string),
            getString(R.string.kiwi_string) , getString(R.string.olive_oil_string))
        applicationPicker.minValue = 0
        applicationPicker.maxValue = applicationArray.size-1
        applicationPicker.displayedValues = applicationArray

        makeDirectory(Utils.rawImageDirectory)
        makeDirectory(Utils.croppedImageDirectory)
        makeDirectory(Utils.processedImageDirectory)
        makeDirectory(Utils.hypercubeDirectory)

        return fragmentApplicationselectorBinding.root
    }

    private fun disableButton(cameraIdNIR: String) {
        if (cameraIdNIR == "No NIR Camera") {
            fragmentApplicationselectorBinding.runApplicationButton.isEnabled = false
            fragmentApplicationselectorBinding.runApplicationButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.sfu_dark_gray))
            fragmentApplicationselectorBinding.runApplicationButton.text = resources.getString(R.string.no_nir_warning)
            fragmentApplicationselectorBinding.runApplicationButton.transformationMethod = null
        }
    }

    private fun enableButton() {
        fragmentApplicationselectorBinding.runApplicationButton.isEnabled = true
        fragmentApplicationselectorBinding.runApplicationButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.sfu_primary))
        fragmentApplicationselectorBinding.runApplicationButton.text = resources.getString(R.string.launch_application_button).uppercase()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onStart() {
        super.onStart()
        val cameraIdNIR = Utils.getCameraIDs(requireContext(), MainActivity.MOBISPECTRAL_APPLICATION).second

        fragmentApplicationselectorBinding.information.setOnClickListener {
            generateAlertBox(requireContext(), "Information", getString(R.string.application_selector_information_string))
        }

        fragmentApplicationselectorBinding.radioGroup.setOnCheckedChangeListener { _, _ ->
            val selectedRadio = fragmentApplicationselectorBinding.radioGroup.checkedRadioButtonId
            val selectedOption = requireView().findViewById<RadioButton>(selectedRadio).text.toString()
            if (selectedOption == getString(R.string.offline_mode_string))
                enableButton()
            else
                disableButton(cameraIdNIR)
        }

        if (cameraIdNIR == "No NIR Camera"){
            fragmentApplicationselectorBinding.onlineMode.isEnabled = false
        }

        fragmentApplicationselectorBinding.galleryButton.setOnClickListener{
            val selectedApplication = applicationArray[fragmentApplicationselectorBinding.applicationPicker.value]
            val selectedRadio = fragmentApplicationselectorBinding.radioGroup.checkedRadioButtonId
            val selectedOption = requireView().findViewById<RadioButton>(selectedRadio).text.toString()
            val offlineMode = selectedOption == getString(R.string.offline_mode_string)
            val sharedPreferences = requireActivity().getSharedPreferences("mobispectral_preferences", Context.MODE_PRIVATE)
            val editor = sharedPreferences?.edit()
            editor!!.putString("application", selectedApplication)
            editor.putString("option", getString(R.string.advanced_option_string))
            editor.putBoolean("offline_mode", offlineMode)
            Log.i("Radio Button", "$selectedApplication, $selectedOption")
            editor.apply()

            startMyActivityForResult()
        }

        fragmentApplicationselectorBinding.filesButton.setOnClickListener{
            startFilesActivityForResult()
        }

        fragmentApplicationselectorBinding.runApplicationButton.setOnTouchListener { _, _ ->
            val selectedApplication = applicationArray[fragmentApplicationselectorBinding.applicationPicker.value]
            val selectedRadio = fragmentApplicationselectorBinding.radioGroup.checkedRadioButtonId
            val selectedOption = requireView().findViewById<RadioButton>(selectedRadio).text.toString()
            val offlineMode = selectedOption == getString(R.string.offline_mode_string)
            val sharedPreferences = requireActivity().getSharedPreferences("mobispectral_preferences", Context.MODE_PRIVATE)
            val editor = sharedPreferences?.edit()
            editor!!.putString("application", selectedApplication)
            editor.putString("option", getString(R.string.advanced_option_string))
            editor.putBoolean("offline_mode", offlineMode)
            Log.i("Radio Button", "$selectedApplication, $selectedOption")
            editor.apply()
            if (selectedApplication == getString(R.string.olive_oil_string))
                generateAlertBox(requireContext(), "Information", getString(R.string.coming_soon_information_string))
            else
                lifecycleScope.launch {
                    withStarted {
                        navController.safeNavigate(ApplicationSelectorFragmentDirections.actionAppselectorToCameraFragment(
                            Utils.getCameraIDs(requireContext(), MainActivity.MOBISPECTRAL_APPLICATION).first, ImageFormat.JPEG)
                        )
                    }
                }
            true
        }
    }
}