package org.wikipedia.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.wikipedia.Constants
import org.wikipedia.R
import org.wikipedia.activity.FragmentUtil
import org.wikipedia.commons.FilePageActivity
import org.wikipedia.databinding.FragmentGalleryItemBinding
import org.wikipedia.page.PageTitle
import org.wikipedia.util.DeviceUtil
import org.wikipedia.util.DimenUtil
import org.wikipedia.util.FeedbackUtil
import org.wikipedia.util.ImageUrlUtil
import org.wikipedia.util.Resource
import org.wikipedia.util.StringUtil
import org.wikipedia.util.log.L
import org.wikipedia.views.ViewUtil
import org.wikipedia.views.imageservice.ImageLoadListener
import org.wikipedia.views.imageservice.ImageService
import kotlin.math.abs

class GalleryItemFragment : Fragment(), MenuProvider {
    interface Callback {
        fun onDownload(item: GalleryItemFragment)
        fun onShare(item: GalleryItemFragment, bitmap: Bitmap?, subject: String, title: PageTitle)
        fun onError(throwable: Throwable)
    }

    private var _binding: FragmentGalleryItemBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GalleryItemViewModel by viewModels()
    private val activityViewModel: GalleryViewModel by activityViewModels()

    private var mediaController: MediaController? = null

    val imageTitle get() = viewModel.imageTitle
    val mediaPage get() = viewModel.mediaPage
    val mediaInfo get() = viewModel.mediaPage?.imageInfo()

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            saveImage()
        } else {
            FeedbackUtil.showMessage(requireActivity(), R.string.gallery_save_image_write_permission_rationale)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGalleryItemBinding.inflate(inflater, container, false)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding.imageView.setOnClickListener {
            if (!isAdded) {
                return@setOnClickListener
            }
            (requireActivity() as GalleryActivity).toggleControls()
        }
        binding.imageView.setOnMatrixChangeListener {
            if (isAdded) {
                binding.imageView.setAllowParentInterceptOnEdge(abs(binding.imageView.scale - 1f) < 0.01f)
            }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch {
                    viewModel.uiState.collect {
                        when (it) {
                            is Resource.Loading -> onLoading(true)
                            is Resource.Success -> onSuccess(it.data)
                            is Resource.Error -> onError(it.throwable)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.imageView.setOnMatrixChangeListener(null)
        binding.imageView.setOnClickListener(null)
        binding.videoThumbnail.setOnClickListener(null)
        _binding = null
        super.onDestroyView()
    }

    override fun onPause() {
        super.onPause()
        mediaController?.let {
            if (binding.videoView.isPlaying) {
                binding.videoView.pause()
            }
            it.hide()
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_gallery, menu)
    }

    override fun onPrepareMenu(menu: Menu) {
        if (!isAdded) {
            return
        }
        val enableShareAndSave = mediaInfo?.thumbUrl?.isNotEmpty() == true && binding.imageView.drawable != null
        menu.findItem(R.id.menu_gallery_visit_image_page).isEnabled = mediaInfo != null
        menu.findItem(R.id.menu_gallery_share).isEnabled = enableShareAndSave
        menu.findItem(R.id.menu_gallery_save).isEnabled = enableShareAndSave
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_gallery_visit_image_page -> {
                startActivity(FilePageActivity.newIntent(requireContext(), imageTitle))
                true
            }
            R.id.menu_gallery_save -> {
                handleImageSaveRequest()
                true
            }
            R.id.menu_gallery_share -> {
                shareImage()
                true
            }
            else -> false
        }
    }

    private fun handleImageSaveRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            saveImage()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun onLoading(visible: Boolean) {
        binding.progressBar.isVisible = visible
    }

    private fun onSuccess(isVideo: Boolean) {
        if (isVideo) {
            loadVideo()
        } else {
            var url = ImageUrlUtil.getUrlForPreferredSize(mediaInfo?.thumbUrl.orEmpty(), Constants.PREFERRED_GALLERY_IMAGE_SIZE)
            if (mediaInfo?.mime?.contains("svg") == true && mediaInfo?.getMetadataTranslations()?.contains(activityViewModel.wikiSite.languageCode) == true) {
                // SVG thumbnails can be rendered with language-specific translations, so let's
                // get the correct URL that points to the appropriate language.
                url = ImageUrlUtil.insertLangIntoThumbUrl(url, activityViewModel.wikiSite.languageCode)
            } else if (mediaInfo?.mime?.contains("gif") == true) {
                // In the case of GIF files, just use the original url, since differently-scaled
                // sizes of the image are not always guaranteed to be animated.
                url = mediaInfo?.originalUrl ?: url
            }
            loadImage(url)
        }
        onLoading(false)
        requireActivity().invalidateOptionsMenu()
        (requireActivity() as GalleryActivity).fetchGalleryDescription(this)
    }

    private fun onError(throwable: Throwable) {
        FeedbackUtil.showMessage(requireActivity(), R.string.gallery_error_draw_failed)
        L.d(throwable)
    }

    private val videoThumbnailClickListener: View.OnClickListener = object : View.OnClickListener {
        private var loading = false
        override fun onClick(v: View) {
            if (loading) {
                return
            }
            val bestUrl = mediaInfo?.getBestDerivativeForSize(Constants.PREFERRED_GALLERY_IMAGE_SIZE)?.src ?: mediaInfo?.originalUrl ?: return
            loading = true
            L.d("Loading video from url: $bestUrl")
            binding.videoView.visibility = View.VISIBLE
            mediaController = MediaController(requireActivity())
            if (!DeviceUtil.isNavigationBarShowing) {
                mediaController?.setPadding(0, 0, 0,
                    DimenUtil.dpToPx(DimenUtil.getNavigationBarHeight(requireContext())).toInt())
            }
            onLoading(true)
            binding.videoView.setMediaController(mediaController)
            binding.videoView.setOnPreparedListener {
                onLoading(false)
                // ...update the parent activity, which will trigger us to start playing!
                (requireActivity() as GalleryActivity).fetchGalleryDescription(this@GalleryItemFragment)
                // hide the video thumbnail, since we're about to start playback
                binding.videoThumbnail.visibility = View.GONE
                binding.videoPlayButton.visibility = View.GONE
                // and start!
                binding.videoView.start()
                loading = false
            }
            binding.videoView.setOnErrorListener { _, _, _ ->
                onLoading(false)
                FeedbackUtil.showMessage(activity!!, R.string.gallery_error_video_failed)
                binding.videoView.visibility = View.GONE
                binding.videoThumbnail.visibility = View.VISIBLE
                binding.videoPlayButton.visibility = View.VISIBLE
                loading = false
                true
            }
            binding.videoView.setVideoURI(Uri.parse(bestUrl))
        }
    }

    private fun loadVideo() {
        binding.videoContainer.visibility = View.VISIBLE
        binding.videoPlayButton.visibility = View.VISIBLE
        binding.videoView.visibility = View.GONE
        if (mediaInfo?.thumbUrl?.isNotEmpty() != true) {
            binding.videoThumbnail.visibility = View.GONE
        } else {
            // show the video thumbnail while the video loads...
            binding.videoThumbnail.visibility = View.VISIBLE
            ViewUtil.loadImage(binding.videoThumbnail, mediaInfo!!.thumbUrl, force = true, listener = GalleryItemImageLoadListener(binding.videoThumbnail))
        }
        binding.videoThumbnail.setOnClickListener(videoThumbnailClickListener)
    }

    private fun loadImage(url: String) {
        binding.imageView.visibility = View.INVISIBLE
        onLoading(true)
        ViewUtil.loadImage(binding.imageView, url, force = true, listener = GalleryItemImageLoadListener(binding.imageView))
    }

    private fun shareImage() {
        mediaInfo?.let {
            val imageUrl = ImageUrlUtil.getUrlForPreferredSize(it.thumbUrl, Constants.PREFERRED_GALLERY_IMAGE_SIZE)
            ImageService.loadImage(requireContext(), imageUrl, onSuccess = { bitmap ->
                if (!isAdded) {
                    return@loadImage
                }
                callback()?.onShare(this@GalleryItemFragment, bitmap,
                    StringUtil.removeHTMLTags(viewModel.imageTitle.displayText), imageTitle)
            })
        }
    }

    private fun saveImage() {
        mediaInfo?.let { callback()?.onDownload(this) }
    }

    private fun callback(): Callback? {
        return FragmentUtil.getCallback(this, Callback::class.java)
    }

    private inner class GalleryItemImageLoadListener(val view: View) : ImageLoadListener {
        override fun onSuccess(image: Any, width: Int, height: Int) {
            if (view.id == binding.imageView.id) {
                binding.imageView.visibility = View.VISIBLE
                (requireActivity() as GalleryActivity).onMediaLoaded()
            }
        }

        override fun onError(error: Throwable) {
            callback()?.onError(error.fillInStackTrace() ?: Throwable(getString(R.string.error_message_generic)))
        }
    }

    companion object {
        const val ARG_GALLERY_ITEM = "galleryItem"

        fun newInstance(pageTitle: PageTitle?, item: MediaListItem): GalleryItemFragment {
            return GalleryItemFragment().apply {
                arguments = bundleOf(Constants.ARG_TITLE to pageTitle, ARG_GALLERY_ITEM to item)
            }
        }
    }
}
