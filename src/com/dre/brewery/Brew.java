package com.dre.brewery;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.BrewerInventory;

import com.dre.brewery.BIngredients;

public class Brew {

	public static Map<Integer, Brew> potions = new HashMap<Integer, Brew>();

	// represents the liquid in the brewed Potions

	private BIngredients ingredients;
	private int quality;
	private int distillRuns;
	private float ageTime;
	private BRecipe currentRecipe;

	public Brew(int uid, BIngredients ingredients) {
		this.ingredients = ingredients;
		potions.put(uid, this);
	}

	// quality already set
	public Brew(int uid, int quality, BRecipe recipe, BIngredients ingredients) {
		this.ingredients = ingredients;
		this.quality = quality;
		this.currentRecipe = recipe;
		potions.put(uid, this);
	}

	// loading from file
	public Brew(int uid, BIngredients ingredients, int quality, int distillRuns, float ageTime, String recipe) {
		this.ingredients = ingredients;
		this.quality = quality;
		this.distillRuns = distillRuns;
		this.ageTime = ageTime;
		this.currentRecipe = BIngredients.getRecipeByName(recipe);
		potions.put(uid, this);
	}

	// returns a Brew by its UID
	public static Brew get(int uid) {
		if (uid < -1) {
			if (!potions.containsKey(uid)) {
				P.p.errorLog("Database failure! unable to find UID " + uid + " of a custom Potion!");
				return null;// throw some exception?
			}
		} else {
			return null;
		}
		return potions.get(uid);
	}

	// returns a Brew by PotionMeta
	public static Brew get(PotionMeta meta) {
		return get(getUID(meta));
	}

	// returns a Brew by ItemStack
	/*
	 * public static Brew get(ItemStack item){ if(item.getTypeId() == 373){
	 * PotionMeta potionMeta = (PotionMeta) item.getItemMeta(); return
	 * get(potionMeta); } return null; }
	 */

	// returns UID of custom Potion item
	public static int getUID(ItemStack item) {
		return getUID((PotionMeta) item.getItemMeta());
	}

	// returns UID of custom Potion meta
	public static int getUID(PotionMeta potionMeta) {
		if (potionMeta.hasCustomEffect(PotionEffectType.REGENERATION)) {
			for (PotionEffect effect : potionMeta.getCustomEffects()) {
				if (effect.getType().equals(PotionEffectType.REGENERATION)) {
					if (effect.getDuration() < -1) {
						return effect.getDuration();
					}
				}
			}
		}
		return 0;
	}

	// remove potion from file (drinking, despawning, should be more!)
	public static void remove(ItemStack item) {
		potions.remove(getUID(item));
	}

	// generate an UID
	public static int generateUID() {
		int uid = -2;
		while (potions.containsKey(uid)) {
			uid -= 1;
		}
		return uid;
	}

	// calculate alcohol from recipe
	public int calcAlcohol() {
		if (currentRecipe != null) {
			int alc = currentRecipe.getAlcohol();
			alc *= ((float) quality / 10.0);
			if (currentRecipe.needsDistilling()) {
				// distillable Potions should have full alc after 6 distills
				float factor = 1.4F / (distillRuns + 1);
				factor += 0.8;
				alc /= factor;
			}
			return alc;
		}
		return 0;
	}

	// calculating quality
	public int calcQuality(BRecipe recipe, byte wood, boolean distilled) {
		// calculate quality from all of the factors
		float quality = (

		ingredients.getIngredientQuality(recipe) +
		ingredients.getCookingQuality(recipe, distilled) +
		ingredients.getWoodQuality(recipe, wood) +
		ingredients.getAgeQuality(recipe, ageTime));

		quality /= 4;
		return (int) Math.round(quality);
	}

	public int getQuality() {
		return quality;
	}

	public boolean canDistill() {
		if (distillRuns >= 6) {
			return false;
		} else {
			if (currentRecipe != null) {
				return currentRecipe.needsDistilling();
			}
		}
		return true;
	}

	// return special effect
	public Map<String, Integer> getEffects() {
		if (currentRecipe != null) {
			return currentRecipe.getEffects();
		}
		return null;
	}

	// Distilling section ---------------

	// distill all custom potions in the brewer
	public static void distillAll(BrewerInventory inv, Boolean[] contents) {
		int slot = 0;
		while (slot < 3) {
			if (contents[slot]) {
				distillSlot(inv, slot);
			}
			slot++;
		}
	}

	// distill custom potion in given slot
	public static void distillSlot(BrewerInventory inv, int slot) {
		ItemStack slotItem = inv.getItem(slot);
		PotionMeta potionMeta = (PotionMeta) slotItem.getItemMeta();
		Brew brew = get(potionMeta);
		BRecipe recipe = brew.ingredients.getdistillRecipe();

		if (recipe != null) {
			// distillRuns will have an effect on the amount of alcohol, not the quality
			brew.quality = brew.calcQuality(recipe, (byte) 0, true);
			brew.distillRuns += 1;
			brew.currentRecipe = recipe;

			// Distill Lore
			String lore = "destilliert";
			String prefix = P.p.color("&7");
			if (brew.distillRuns > 1) {
				prefix = prefix + brew.distillRuns + "-fach ";
			}
			addOrReplaceLore(potionMeta, prefix, lore);
			addOrReplaceEffects(potionMeta, brew.getEffects());

			P.p.log("destilled " + recipe.getName(5) + " has Quality: " + brew.quality + ", alc: " + brew.calcAlcohol());

			potionMeta.setDisplayName(P.p.color("&f" + recipe.getName(brew.quality)));

			// if the potion should be further distillable
			if (recipe.getDistillRuns() > 1 && brew.distillRuns <= 5) {
				slotItem.setDurability(PotionColor.valueOf(recipe.getColor()).getColorId(true));
			} else {
				slotItem.setDurability(PotionColor.valueOf(recipe.getColor()).getColorId(false));
			}
		} else {
			potionMeta.setDisplayName(P.p.color("&f" + "Undefinierbares Destillat"));
			slotItem.setDurability(PotionColor.GREY.getColorId(brew.distillRuns <= 5));
		}

		slotItem.setItemMeta(potionMeta);
	}

	// Ageing Section ------------------

	public static void age(ItemStack item, float time, byte wood) {
		PotionMeta potionMeta = (PotionMeta) item.getItemMeta();
		Brew brew = get(potionMeta);
		if (brew != null) {
			brew.ageTime += time;
			// if younger than half a day, it shouldnt get aged form
			if (brew.ageTime > 0.5) {
				BRecipe recipe = brew.ingredients.getAgeRecipe(wood, brew.ageTime, brew.distillRuns > 0);
				if (recipe != null) {
					brew.quality = brew.calcQuality(recipe, wood, brew.distillRuns > 0);
					brew.currentRecipe = recipe;
					P.p.log("Final " + recipe.getName(5) + " has Quality: " + brew.quality);

					if (brew.ageTime >= 1) {
						String lore = " Fassgereift";
						String prefix = P.p.color("&7");
						if (brew.ageTime == 1) {
							prefix = prefix + "Ein Jahr";
						} else if (brew.ageTime < 201) {
							prefix = prefix + (int) Math.floor(brew.ageTime) + " Jahre";
						} else {
							prefix = prefix + "Hunderte Jahre";
						}
						addOrReplaceLore(potionMeta, prefix, lore);
						addOrReplaceEffects(potionMeta, brew.getEffects());
					}

					potionMeta.setDisplayName(P.p.color("&f" + recipe.getName(brew.quality)));
					item.setDurability(PotionColor.valueOf(recipe.getColor()).getColorId(false));
					item.setItemMeta(potionMeta);
				}
			}
		}
	}

	public static void addOrReplaceEffects(PotionMeta meta, Map<String, Integer> effects) {
		if (effects != null) {
			for (Map.Entry<String, Integer> entry : effects.entrySet()) {
				if (!entry.getKey().endsWith("X")) {
					PotionEffectType type = PotionEffectType.getByName(entry.getKey());
					if (type != null) {
						meta.addCustomEffect(type.createEffect(0, 0), true);
					}
				}
			}

		}
	}

	public static void addOrReplaceLore(PotionMeta meta, String prefix, String lore) {
		if (meta.hasLore()) {
			List<String> existingLore = meta.getLore();
			int index = indexOfSubstring(existingLore, lore);
			if (index > -1) {
				existingLore.set(index, prefix + lore);
			} else {
				existingLore.add(prefix + lore);
			}
			meta.setLore(existingLore);
			return;
		}
		List<String> newLore = new ArrayList<String>();
		newLore.add("");
		newLore.add(prefix + lore);
		meta.setLore(newLore);
	}

	public static int indexOfSubstring(List<String> list, String substring) {
		for (int index = 0; index < list.size(); index++) {
			String string = list.get(index);
			if (string.contains(substring)) {
				return index;
			}
		}
		return -1;
	}

	// gets the Color that represents a quality in Lore
	public static String getQualityColor(int quality) {
		String color;
		if (quality > 8) {
			color = "&a";
		} else if (quality > 6) {
			color = "&e";
		} else if (quality > 4) {
			color = "&6";
		} else if (quality > 2) {
			color = "&c";
		} else {
			color = "&4";
		}
		return P.p.color(color);
	}

	// Saves all data
	public static void save(ConfigurationSection config) {
		for (int uid : potions.keySet()) {
			ConfigurationSection idConfig = config.createSection("" + uid);
			Brew brew = potions.get(uid);
			// not saving unneccessary data
			if (brew.quality != 0) {
				idConfig.set("quality", brew.quality);
			}
			if (brew.distillRuns != 0) {
				idConfig.set("distillRuns", brew.distillRuns);
			}
			if (brew.ageTime != 0) {
				idConfig.set("ageTime", brew.ageTime);
			}
			if (brew.currentRecipe != null) {
				idConfig.set("recipe", brew.currentRecipe.getName(5));
			}
			// save the ingredients
			brew.ingredients.save(idConfig.createSection("ingredients"));
		}
	}

	public static enum PotionColor {
		PINK(1),
		CYAN(2),
		ORANGE(3),
		GREEN(4),
		BRIGHT_RED(5),
		BLUE(6),
		BLACK(8),
		RED(9),
		GREY(10),
		WATER(11),
		DARK_RED(12),
		BRIGHT_GREY(14);

		private final int colorId;

		private PotionColor(int colorId) {
			this.colorId = colorId;
		}

		// gets the Damage Value, that sets a color on the potion
		// offset +32 is not accepted by brewer, so not further destillable
		public short getColorId(boolean destillable) {
			if (destillable) {
				return (short) (colorId + 64);
			}
			return (short) (colorId + 32);
		}
	}

}