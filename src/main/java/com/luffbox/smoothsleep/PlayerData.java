package com.luffbox.smoothsleep;

import com.luffbox.smoothsleep.lib.*;
import com.luffbox.smoothsleep.tasks.WakeParticlesTask;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;

import static com.luffbox.smoothsleep.lib.ConfigHelper.WorldSettingKey.*;

/**
 * Contains data about a Player that SmoothSleep will use later
 */
public class PlayerData implements Purgeable {

	private final SmoothSleep pl;
	private final PlayerTimers timers;
	private final Player plr;
	private boolean ignorePerm = false;
	private BossBar bar;
	private boolean woke = false;

	public PlayerData(SmoothSleep plugin, Player player) {
		SmoothSleep.logDebug("Initializing Player data for " + player.getName());
		pl = plugin;
		plr = player;
		timers = new PlayerTimers();
		update();
	}

	public ConfigHelper.WorldSettings worldConf() {
		return pl.data.config.worlds.get(plr.getWorld());
	}
	public WorldData worldData() { return pl.data.getWorldData(plr); }

	// Add anything that needs to be checked on join or world change here.
	// Do not call on sleep tick! (Will cause perm check every tick)
	public void update() {
		updateIgnorePerm();
		updateUI();
	}

	public void updateUI() {
		updateBossBar();
		updateActionBar();
		updateTitles();
	}

	public void clearTitles() {
		plr.sendTitle(" ", " ", 0, 0, 0);
	}
	public void updateTitles() {
		if (!worldConf().getBoolean(TITLES_ENABLED)) return;
		if (!worldData().isNight() || !isSleeping()) {
			if (woke) plr.sendTitle(mrnTitle(), mrnSubtitle(), 0, worldConf().getInt(TITLE_STAY), worldConf().getInt(TITLE_FADE));
			woke = false;
			return;
		}
		plr.sendTitle(slpTitle(), slpSubtitle(), 0, worldConf().getInt(TITLE_STAY), worldConf().getInt(TITLE_FADE));
	}

	public void clearActionBar() { pl.data.actionBarHelper.sendActionBar(plr, " "); }
	public void updateActionBar() {
		if (!worldConf().getBoolean(ACTIONBAR_ENABLED)) { return; }
		if (!isSleeping() && !worldConf().getBoolean(ACTIONBAR_WAKERS)) { return; }
		if (!worldData().isNight() || worldData().getSleepers().isEmpty()) { clearActionBar(); return; }
		pl.data.actionBarHelper.sendActionBar(plr, actionBarTitle());
	}

	public void updateBossBar() {
		if (!worldConf().getBoolean(BOSSBAR_ENABLED)) { hideBossBar(); return; }
		if (!isSleeping() && !worldConf().getBoolean(BOSSBAR_WAKERS)) { hideBossBar(); return; }
		if (worldData().isNight() && !worldData().getSleepers().isEmpty()) {
			if (bar == null) { createBossBar(); }
			bar.setTitle(bossBarTitle());
			bar.setColor(worldConf().getBarColor(BOSSBAR_COLOR));
			bar.setStyle(worldConf().getBarStyle(BOSSBAR_STYLE));
			bar.setProgress(worldData().getTimeRatio());
			showBossBar();
		} else { hideBossBar(); }
	}

	public void createBossBar() {
		if (bar == null) {
			bar = pl.getServer().createBossBar("", BarColor.BLUE, BarStyle.SOLID); // For less repetition, create then update
			bar.addPlayer(plr);
		}
	}

	public void showBossBar() { if (bar != null) bar.setVisible(true); }
	public void hideBossBar() { if (bar != null) bar.setVisible(false); }

	// Health and food is clamped to prevent IllegalArgumentException
	public void tickTimers(double ticks) {
		timers.incAll(ticks);

		int maxFeed = 20;
		if (isSleeping() || worldConf().getBoolean(FEED_AWAKE)) {
			if (!plr.hasPermission("smoothsleep.ignorefeed")) {
				while (timers.getFood() >= worldConf().getInt(FEED_TICKS)) {
					timers.decFood(worldConf().getInt(FEED_TICKS));
					int val = plr.getFoodLevel() + worldConf().getInt(FEED_AMOUNT);
					boolean addSat = worldConf().getBoolean(ADD_SATURATION) && val >= maxFeed;
					val = MiscUtils.clamp(val, 0, maxFeed);
					plr.setFoodLevel(val);
					if (addSat && plr.getSaturation() < worldConf().getDouble(MAX_SATURATION)) {
						// Add saturation, clamp to food level, as per https://minecraft.gamepedia.com/Hunger#Mechanics
						double sat = plr.getSaturation() + worldConf().getDouble(SATURATION_AMOUNT);
						sat = MiscUtils.clamp(sat, 0.0, worldConf().getDouble(MAX_SATURATION));
						plr.setSaturation((float) sat);
					}
				}
			}
		}

		if (isSleeping() || worldConf().getBoolean(HEAL_AWAKE)) {
			if (!plr.hasPermission("smoothsleep.ignoreheal")) {
				AttributeInstance mli = plr.getAttribute(Attribute.GENERIC_MAX_HEALTH);
				double maxLife = mli == null ? 20 : mli.getValue();
				while (timers.getHeal() >= worldConf().getInt(HEAL_TICKS)) {
					timers.decHeal(worldConf().getInt(HEAL_TICKS));
					double val = plr.getHealth() + worldConf().getInt(HEAL_AMOUNT);
					val = MiscUtils.clamp(val, 0, maxLife);
					plr.setHealth(val);
				}
			}
		}
	}

	public Player getPlayer() { return plr; }

	public boolean isSleeping() { return plr.isSleeping(); }

	public PlayerTimers getTimers() { return timers; }

	// Checks if player has ignore perm or is otherwise ignoring sleepers
	public boolean isSleepingIgnored() {
		boolean ignore = hasIgnorePerm() || plr.isSleepingIgnored();
		if (!ignore && plr.getGameMode() == GameMode.SPECTATOR) ignore = true;
		if (!ignore && worldConf().getBoolean(IGNORE_VANISH) && pl.data.userHelper.isVanished(plr)) ignore = true;
		return ignore;
	}

	// Checks SmoothSleep's ignore permission
	private boolean hasIgnorePerm() { return ignorePerm; }

	// Only check this when player joins or changes world to minimize perm checks.
	public void updateIgnorePerm() { ignorePerm = plr.hasPermission(SmoothSleep.PERM_IGNORE); }

	public void wake() {
		boolean complete = worldData().hasFinishedSleeping(getPlayer());
		if (complete) {
			woke = true;

			// Run wake particle task
			WakeParticlesTask wpt = new WakeParticlesTask(pl, this);
			wpt.runTaskTimer(pl, 5, worldConf().getInt(PARTICLE_DELAY));

			// Play wake sound
			if (worldConf().getSound(MORNING_SOUND) != null) {
				getPlayer().playSound(getPlayer().getLocation(), worldConf().getSound(MORNING_SOUND), 1.0f, 1.0f);
			}

			// Apply sleep reward effects
			if (worldConf().getBoolean(REWARD_EFFECT_ENABLED) && getPlayer().hasPermission("smoothsleep.sleepreward")) {
				ConfigurationSection potFx = worldConf().getConfSection(REWARD_EFFECT_LIST);
				if (!potFx.getKeys(false).isEmpty()) {
					if ((int) timers.getSlpt() / 1000L >= worldConf().getInt(REWARD_EFFECT_SLEEP_HOURS)) {
						Set<PotionEffect> effects = new HashSet<>();
						boolean particles = worldConf().getBoolean(REWARD_EFFECT_PARTICLES);
						for (String key : potFx.getKeys(false)) {
							PotionEffectType pet = ConfigHelper.getPotionEffect(key);
							if (pet != null && potFx.getInt(key + ".duration") > 0L) {
								effects.add(new PotionEffect(pet,
										potFx.getInt(key + ".duration"),
										potFx.getInt(key + ".amplifier"),
										true, particles, true
								));
							}
						}
						for (PotionEffect pe : effects) {
							PotionEffect curFx = getPlayer().getPotionEffect(pe.getType());
							if (curFx == null || curFx.getAmplifier() < pe.getAmplifier() || curFx.getDuration() < pe.getDuration()) {
								getPlayer().addPotionEffect(pe);
							}
						}
					}
				}
			}
			updateUI();
		} else {
			clearTitles();
			getTimers().resetAll();
			if (worldData().getSleepers().isEmpty()) {
				for (PlayerData pd : worldData().getPlayerData()) {
					pd.clearActionBar();
					pd.hideBossBar();
				}
			} else {
				if (!worldConf().getBoolean(ConfigHelper.WorldSettingKey.ACTIONBAR_WAKERS)) clearActionBar();
				if (!worldConf().getBoolean(ConfigHelper.WorldSettingKey.BOSSBAR_WAKERS)) hideBossBar();
			}
		}
		if (worldConf().getBoolean(HEAL_NEG_STATUS)) {
			if ((int) timers.getSlpt() / 1000L >= worldConf().getInt(HOURS_NEG_STATUS)) {
				ConfigHelper.negativeEffects.forEach(plr::removePotionEffect);
			}
		}
		if (worldConf().getBoolean(HEAL_POS_STATUS)) {
			if ((int) timers.getSlpt() / 1000L >= worldConf().getInt(HOURS_POS_STATUS)) {
				ConfigHelper.positiveEffects.forEach(plr::removePotionEffect);
			}
		}
		timers.resetAll();
		setSleepTicks(100);
	}

	@Override
	public void purgeData() {
		if (bar != null) {
			bar.removeAll();
			bar = null;
		}
	}

	private String subStr(String template) {
		return pl.data.placeholders.replace(template, plr.getWorld(), plr, worldData().getSleepers().size(), worldData().getWakers().size(),
				worldData().getTimescale(), (int) getTimers().getSlpt(), pl.data.userHelper.getNickname(plr));
	}

	// Some short-hand methods to assist with placeholder variables
	private String slpTitle() { return MiscUtils.trans(subStr(worldConf().getString(SLEEP_TITLE))); }
	private String slpSubtitle() { return MiscUtils.trans(subStr(worldConf().getString(SLEEP_SUBTITLE))); }
	private String mrnTitle() { return MiscUtils.trans(subStr(worldConf().getString(MORNING_TITLE))); }
	private String mrnSubtitle() { return MiscUtils.trans(subStr(worldConf().getString(MORNING_SUBTITLE))); }
	private String actionBarTitle() { return MiscUtils.trans(subStr(worldConf().getString(ACTIONBAR_TITLE))); }
	private String bossBarTitle() { return MiscUtils.trans(subStr(worldConf().getString(BOSSBAR_TITLE))); }

	public void setSleepTicks(long ticks) {
//		try {
//			Object nmsPlr = ReflectUtil.invokeMethod(plr, "getHandle");
//			ReflectUtil.setValue(nmsPlr, false, "sleepTicks", (int) ticks);
//		} catch (Exception e) {
//			SmoothSleep.logSevere("Failed to set sleep ticks for " + plr.getName());
//		}
	}
}
