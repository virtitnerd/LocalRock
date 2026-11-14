package com.kodraliu.localrock.shared.vacuum

/** History, Maintenance and Firmware flags (track B4); the roller mop (bit 128) is Capabilities.advertised.rollerMop. */
internal data class SecondaryFlags(
    val cruiseHistory: Boolean,
    val firmwareUpdates: Boolean,
    val autoFirmwareUpdates: Boolean,
)

internal fun secondaryFlags(nfs: String?, patrol: Boolean): SecondaryFlags = SecondaryFlags(
    cruiseHistory = patrol, // official isVideoPatrolSupported
    firmwareUpdates = true, // isSupportFirmwareUpdate is a host-app check; LocalRock always offers its server update
    autoFirmwareUpdates = newFeatureBit(nfs, 6),
)
