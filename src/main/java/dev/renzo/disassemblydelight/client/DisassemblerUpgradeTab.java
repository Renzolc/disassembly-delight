package dev.renzo.disassemblydelight.client;

import java.util.List;

import dev.renzo.disassemblydelight.compat.sb.DisassemblerUpgradeContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeSettingsTab;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.Label;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.WidgetBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Dimension;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.GuiHelper;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position;

public class DisassemblerUpgradeTab extends UpgradeSettingsTab<DisassemblerUpgradeContainer> {
    public DisassemblerUpgradeTab(DisassemblerUpgradeContainer upgradeContainer, Position position, StorageScreenBase<?> screen) {
        super(upgradeContainer, position, screen,
                Component.translatable("gui.disassembly_delight.disassembler_upgrade"),
                Component.translatable("gui.disassembly_delight.disassembler_upgrade.tooltip"));
        addHideableChild(new Label(new Position(x + 32, y + 24),
                Component.translatable("gui.disassembly_delight.disassembler_upgrade.into_backpack")));
        addHideableChild(new Preview(new Position(x + 36, y + 40), new Dimension(54, 54), upgradeContainer));
    }

    @Override
    protected void moveSlotsToTab() {
        List<Slot> slots = getContainer().getSlots();
        if (slots.isEmpty()) {
            return;
        }
        Slot input = slots.getFirst();
        input.x = x + 8 - screen.getGuiLeft();
        input.y = y + 40 - screen.getGuiTop();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, Minecraft minecraft, int mouseX, int mouseY) {
        super.renderBg(guiGraphics, minecraft, mouseX, mouseY);
        if (isOpen) {
            GuiHelper.renderSlotsBackground(guiGraphics, x + 7, y + 39, 1, 1);
        }
    }

    /** Drawn items only. These are not container slots, so they are not part of stack sync. */
    private static final class Preview extends WidgetBase {
        private final DisassemblerUpgradeContainer container;

        protected Preview(Position position, Dimension dimension, DisassemblerUpgradeContainer container) {
            super(position, dimension);
            this.container = container;
        }

        @Override
        protected void renderBg(GuiGraphics guiGraphics, Minecraft minecraft, int mouseX, int mouseY) {
            GuiHelper.renderSlotsBackground(guiGraphics, x, y, 3, 3);
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
            List<ItemStack> results = results();
            int shown = Math.min(9, results.size());
            for (int i = 0; i < shown; i++) {
                int ix = x + 1 + (i % 3) * 18;
                int iy = y + 1 + (i / 3) * 18;
                ItemStack stack = results.get(i);
                guiGraphics.renderItem(stack, ix, iy);
                guiGraphics.renderItemDecorations(font, stack, ix, iy);
            }
        }

        @Override
        public void renderTooltip(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY) {
            List<ItemStack> results = results();
            int shown = Math.min(9, results.size());
            for (int i = 0; i < shown; i++) {
                int ix = x + 1 + (i % 3) * 18;
                int iy = y + 1 + (i / 3) * 18;
                if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                    guiGraphics.renderTooltip(font, results.get(i), mouseX, mouseY);
                    return;
                }
            }
        }

        private List<ItemStack> results() {
            if (minecraft.level == null) {
                return List.of();
            }
            return container.getUpgradeWrapper().preview(minecraft.level);
        }
    }
}
