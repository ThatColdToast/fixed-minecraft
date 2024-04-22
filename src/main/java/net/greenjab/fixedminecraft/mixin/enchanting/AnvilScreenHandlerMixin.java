package net.greenjab.fixedminecraft.mixin.enchanting;

import com.google.common.collect.Maps;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.HashMap;
import java.util.Map;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin extends ForgingScreenHandler{
    @Shadow
    @Final
    private Property levelCost;

    @Shadow
    private int repairItemUsage;

    public AnvilScreenHandlerMixin(@Nullable ScreenHandlerType<?> type, int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(type, syncId, playerInventory, context);
    }

    /**
     * @author ThatColdToast
     * @reason To completely change the way anvils work
     */
    @Overwrite
    public void updateResult() {
        System.out.println("----- [Update Result] -----");
        ItemStack left = this.input.getStack(0);
        ItemStack right = this.input.getStack(1);

        // Filter if there are any empty slots
        System.out.println("Filter empty");
        if (left.isEmpty() || right.isEmpty()) {
            this.levelCost.set(0);
            this.output.setStack(0, ItemStack.EMPTY);
            this.sendContentUpdates();
            return;
        }

        Map<Enchantment, Integer> leftEnchantments = EnchantmentHelper.get(left);
        Map<Enchantment, Integer> rightEnchantments = EnchantmentHelper.get(right);

        // Filter mutually exclusive enchants
        System.out.println("Filter mutually exclusive");
        for (Enchantment leftEnchant : leftEnchantments.keySet()) {
            for (Enchantment rightEnchant : rightEnchantments.keySet()) {
                if (!leftEnchant.canCombine(rightEnchant) && leftEnchant != rightEnchant) {
                    this.levelCost.set(0);
                    this.output.setStack(0, ItemStack.EMPTY);
                    this.sendContentUpdates();
                    return;
                }
            }
        }

        Map<Enchantment, Integer> resultEnchantments = Maps.newLinkedHashMap();

        // Add all enchants
        System.out.println("Add all enchants");
        resultEnchantments.putAll(leftEnchantments);
        resultEnchantments.putAll(rightEnchantments);

        // Get resultant enchant levels
        System.out.println("Get result enchant levels");
        for (Enchantment enchantment : resultEnchantments.keySet()) {
            int leftLevel = leftEnchantments.getOrDefault(enchantment, 0);
            int rightLevel = rightEnchantments.getOrDefault(enchantment, 0);

            int level;
            if (leftLevel == rightLevel)
                level = leftLevel + 1;
            else
                level = Math.max(leftLevel, rightLevel);

            if (level > enchantment.getMaxLevel())
                level = enchantment.getMaxLevel();

            resultEnchantments.put(enchantment, level);
            //System.out.println("Level: " + level + " Left: " + leftLevel + " Right: " + rightLevel);
        }

        // Pick result
        System.out.println("Pick result and other");
        ItemStack result;
        ItemStack other;
        if (left.isDamageable()) {
            result = left.copy();
            other = right.copy();
        } else if (right.isDamageable()) {
            result = right.copy();
            other = left.copy();
        } else {
            result = left.copy();
            other = right.copy();
        }

        // Filter enchantments on the wrong tools
        System.out.println("Filter wrong tools");
        for (Enchantment enchantment : resultEnchantments.keySet()) {
            if (!enchantment.isAcceptableItem(result)) {
                this.levelCost.set(0);
                this.output.setStack(0, ItemStack.EMPTY);
                this.sendContentUpdates();
                return;
            }
        }

        // Repair items
        System.out.println("Repair items");
        if (result.isDamageable()) {
            if (result.getItem() == other.getItem()) {
                int newDamage = result.getDamage() - (other.getMaxDamage() - other.getDamage());
                result.setDamage(newDamage);
            } else if (result.getItem().canRepair(result, other)) {
                int repairDelta = Math.min(result.getDamage(), result.getMaxDamage() / 4);
                this.repairItemUsage = 0;
                for (int i = 0; i < other.getCount(); i++) {
                    int newDamage = result.getDamage() - repairDelta;
                    result.setDamage(newDamage);
                    this.repairItemUsage++;
                    if (result.getDamage() == 0) {
                        System.out.println("repairItemUsage: " + this.repairItemUsage);
                        break;
                    }
                    repairDelta = Math.min(result.getDamage(), result.getMaxDamage() / 4);
                }
            } else if (!other.isOf(Items.ENCHANTED_BOOK)) {
                this.levelCost.set(0);
                this.output.setStack(0, ItemStack.EMPTY);
                this.sendContentUpdates();
                return;
            }
        }

        // Calculate cost
        System.out.println("Calculate cost");
        int cost = 0;
        for (Enchantment enchantment : resultEnchantments.keySet()) {
            int scalar = 6;
            int level  = resultEnchantments.get(enchantment);
            int max = enchantment.getMaxLevel();
            //System.out.println("E: " + enchantment.getName(level).getString() + " M: " + max + " S: " + (scalar * level) + " C: " + ((scalar * level) / max));

            if (!enchantment.isCursed())
                cost += (scalar * level) / max;
            else
                cost -= (scalar * level) / max;
        }

        // Filter cost too high
        System.out.println("Filter cost");
        if (cost > 15) {
            this.levelCost.set(0);
            this.output.setStack(0, ItemStack.EMPTY);
            this.sendContentUpdates();
            return;
        }

        // Set result information
        EnchantmentHelper.set(resultEnchantments, result);

        this.levelCost.set(cost);
        this.output.setStack(0, result);
        this.sendContentUpdates();
    }
}
