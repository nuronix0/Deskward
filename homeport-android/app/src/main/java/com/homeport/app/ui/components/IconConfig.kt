package com.homeport.app.ui.components

import com.homeport.app.R
import com.homeport.app.domain.model.DeviceStatus
import com.homeport.app.domain.model.DeviceType
import com.homeport.app.domain.model.FileCategory
import com.homeport.app.domain.model.FileItem

/**
 * Centralized Icon Configuration & Fallback Engine.
 *
 * [USE_CUSTOM_3D_ICONS]: Master toggle switch.
 * - Set to `true` to use the user's high-fidelity 3D custom rendered icons.
 * - Set to `false` to INSTANTLY revert 100% of the UI back to the built-in
 *   vector 3D caustic icons with zero side-effects.
 */
object IconConfig {

    /**
     * Master Switch: Set to false to enforce the cohesive Obsidian Liquid Glass icon engine!
     */
    const val USE_CUSTOM_3D_ICONS: Boolean = false

    /**
     * Resolves the custom 3D drawable resource for a device based on its
     * form factor (Phone, Tablet, Laptop, Desktop), its live mesh status (Online vs Offline),
     * and its operating system / platform (Windows, macOS/Apple, Linux, Android, Arch).
     */
    fun getCustomDeviceIconRes(
        type: DeviceType,
        status: DeviceStatus = DeviceStatus.ONLINE,
        platform: String? = null
    ): Int? {
        if (!USE_CUSTOM_3D_ICONS) return null

        val isOnline = (status == DeviceStatus.ONLINE || status == DeviceStatus.BUSY)
        val plat = platform?.lowercase().orEmpty()

        return when (type) {
            DeviceType.PHONE -> {
                if (isOnline) R.drawable.ic_3d_phone_on else R.drawable.ic_3d_phone_off
            }
            DeviceType.TABLET -> {
                if (isOnline) R.drawable.ic_3d_tablet_on else R.drawable.ic_3d_tablet_off
            }
            DeviceType.LAPTOP -> {
                if (isOnline) R.drawable.ic_3d_laptop_on else R.drawable.ic_3d_laptop_off
            }
            DeviceType.DESKTOP,
            DeviceType.COMPUTER -> {
                when {
                    "win" in plat -> R.drawable.ic_3d_platform_windows
                    "mac" in plat || "apple" in plat || "darwin" in plat -> R.drawable.ic_3d_platform_apple
                    "arch" in plat -> R.drawable.ic_3d_platform_arch
                    "linux" in plat || "ubuntu" in plat || "debian" in plat || "fedora" in plat -> R.drawable.ic_3d_platform_linux
                    isOnline -> R.drawable.ic_3d_laptop_on
                    else -> R.drawable.ic_3d_laptop_off
                }
            }
        }
    }

    /**
     * Resolves custom 3D drawable resource for a file or directory based on its
     * file extension and semantic category.
     */
    fun getCustomFileIconRes(file: FileItem): Int? {
        if (!USE_CUSTOM_3D_ICONS) return null

        if (file.isDirectory) {
            return R.drawable.ic_3d_file_folder
        }

        val ext = file.extension.lowercase().removePrefix(".")
        val name = file.name.lowercase()

        // 1. High-precision extension mapping
        when (ext) {
            "pdf" -> return R.drawable.ic_3d_file_pdf
            "md", "markdown" -> return R.drawable.ic_3d_file_markdown
            "apk", "aab", "xapk" -> return R.drawable.ic_3d_platform_android
            "exe", "msi" -> return R.drawable.ic_3d_platform_windows
            "dmg", "pkg", "ipa" -> return R.drawable.ic_3d_platform_apple
            "fig", "sketch", "xd", "psd", "ai" -> return R.drawable.ic_3d_file_design
        }

        // 2. Universal category mapping
        return when (file.category) {
            FileCategory.CODE -> R.drawable.ic_3d_file_code
            FileCategory.DOCUMENT -> R.drawable.ic_3d_file_docs
            FileCategory.IMAGE -> R.drawable.ic_3d_file_images
            FileCategory.VIDEO -> R.drawable.ic_3d_file_video
            FileCategory.AUDIO -> R.drawable.ic_3d_file_audio
            FileCategory.ARCHIVE -> R.drawable.ic_3d_file_archive
            FileCategory.DESIGN -> R.drawable.ic_3d_file_design
            FileCategory.MODEL_3D -> R.drawable.ic_3d_mesh_cube
            FileCategory.CONFIG,
            FileCategory.SYSTEM,
            FileCategory.SECURITY -> R.drawable.ic_3d_file_system
            FileCategory.APPLICATION -> {
                when {
                    "apk" in name -> R.drawable.ic_3d_platform_android
                    "exe" in name -> R.drawable.ic_3d_platform_windows
                    else -> R.drawable.ic_3d_platform_android
                }
            }
            FileCategory.DATABASE -> R.drawable.ic_3d_file_system
            FileCategory.FONT -> R.drawable.ic_3d_file_docs
            FileCategory.UNKNOWN -> R.drawable.ic_3d_file_folder
        }
    }
}
