package dev.renzo.disassemblydelight.compat.sb;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeGroup;

public final class OnePerStorageLimit implements IUpgradeCountLimitConfig {
    public static final OnePerStorageLimit INSTANCE = new OnePerStorageLimit();

    private OnePerStorageLimit() {}

    @Override
    public int getMaxUpgradesPerStorage(String storageType, @Nullable ResourceLocation upgradeRegistryName) {
        return 1;
    }

    @Override
    public int getMaxUpgradesInGroupPerStorage(String storageType, UpgradeGroup upgradeGroup) {
        return Integer.MAX_VALUE;
    }
}
