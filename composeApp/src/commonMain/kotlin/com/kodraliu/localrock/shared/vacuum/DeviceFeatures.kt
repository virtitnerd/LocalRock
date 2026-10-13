package com.kodraliu.localrock.shared.vacuum

/**
 * What a robot says it supports, read from its `app_get_init_status` answer the way the official app
 * does: a 64-bit `new_feature_info` and a hex `new_feature_info_str`, each flag one bit or mask. The
 * bit positions are python-roborock's `DeviceFeatures` table.
 *
 * Only flags that the robot itself reports are here. python-roborock decides some others (water
 * modes, mop routes) from the product model, which is not known on the phone, so those are left to
 * the existing screens rather than guessed.
 */
data class DeviceFeatures(
    val drying: Boolean,
    val valleyElectricity: Boolean,
    val superDeepWash: Boolean,
    val dirtyReplenishClean: Boolean,
    val dustCollectionSetting: Boolean,
    val hotWashTowel: Boolean,
    val carpetDeepClean: Boolean,
) {
    companion object {
        fun fromInitStatus(newFeatureInfo: Long, newFeatureInfoStr: String): DeviceFeatures {
            // new_feature_info bits 32+ ("upper_32_bits" in python-roborock).
            fun upperBit(index: Int): Boolean = ((newFeatureInfo ushr 32) ushr index) and 1L == 1L

            // The last `slice` hex characters of the string, tested against a mask.
            fun strMask(mask: Long, slice: Int): Boolean {
                if (newFeatureInfoStr.length < slice) return false
                val value = newFeatureInfoStr.takeLast(slice).toLongOrNull(16) ?: return false
                return mask and value != 0L
            }

            // One bit of the hex string, counted from its end (4 bits per character).
            fun strBit(bit: Int): Boolean {
                val fromEnd = 1 + bit / 4
                if (fromEnd > newFeatureInfoStr.length) return false
                val nibble = newFeatureInfoStr[newFeatureInfoStr.length - fromEnd].digitToIntOrNull(16) ?: return false
                return (nibble shr (bit % 4)) and 1 == 1
            }

            // Lower 32 bits of new_feature_info ("robot_new_features").
            fun robotNew(mask: Long): Boolean = mask and newFeatureInfo != 0L

            return DeviceFeatures(
                drying = upperBit(15),
                valleyElectricity = upperBit(13),
                superDeepWash = strMask(32768, 8),
                dirtyReplenishClean = strBit(34),
                dustCollectionSetting = robotNew(33554432),
                hotWashTowel = strBit(41),
                carpetDeepClean = strMask(8, 8),
            )
        }
    }
}
