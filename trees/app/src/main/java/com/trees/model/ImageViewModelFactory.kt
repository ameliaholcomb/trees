package com.trees.model

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.trees.common.helpers.ImageStoreInterface
import com.trees.common.pyi.ImageProcessorInterface

class ImageViewModelFactory(
        private var imageStoreInterface: ImageStoreInterface? = null,
        private var imageProcessorInterface: ImageProcessorInterface? = null,
        owner: SavedStateRegistryOwner,
        defaultArgs: Bundle? = null
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
    override fun <T : ViewModel> create(
            key: String,
            modelClass: Class<T>,
            handle: SavedStateHandle
    ): T {
        return ImageViewModel(handle, imageProcessorInterface, imageStoreInterface) as T
    }
}