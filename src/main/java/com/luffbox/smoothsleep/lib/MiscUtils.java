package com.luffbox.smoothsleep.lib;

import com.luffbox.smoothsleep.SmoothSleep;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bukkit.ChatColor.*;

public class MiscUtils {

	/**
	 * Calculates a value in a new range equivilent to the old value's position in the old range. This function makes
	 * no attempt to verify valid values and may produce odd results if min/max values are reversed or if the old value
	 * is not within the specified old range. If the old range values are equal (min = max), a divide by zero error
	 * will likely be thrown.
	 * @param clamp Whether to clamp the output to the new range. If true and the old value is outside of the old range,
	 *              it will return either newMin (if value is below old range) or newMax (if above the old range).
	 * @param oldMin The old minimum value. Establishes the lower end of the old range.
	 * @param oldMax The old maximum value. Establishes the upper end of the old range.
	 * @param newMin The new minimum value. Establishes the lower end of the new range.
	 * @param newMax The new maximum value. Establishes the upper end of the new range.
	 * @param value The old value. The position of this value in the old range will determine the position in the new range.
	 * @return Returns a value that's in an equivilent position in the new range as the old value was in the old range.
	 */
	public static double remapValue(boolean clamp, double oldMin, double oldMax, double newMin, double newMax, double value) {
		if (clamp) {
			if (value >= oldMax) return newMax;
			if (value <= oldMin) return newMin;
		}
		return (((newMax - newMin) * (value - oldMin)) / (oldMax - oldMin)) + newMin;
	}

	public static double clamp(double val, double min, double max) { return Math.min(max, Math.max(min, val)); }
	public static int clamp(int val, int min, int max) { return Math.min(max, Math.max(min, val)); }

	public static int ticksTo24Hours(long ticks) {
		ticks += 6000;
		int hours = (int) ticks / 1000;
		return (hours >= 24 ? hours - 24 : hours);
	}
	public static int ticksTo12Hours(long ticks) {
		int hours = ticksTo24Hours(ticks);
		return hours > 12 ? hours - 12 : hours == 0 ? 12 : hours;
	}
	public static int ticksToMinutes(long ticks) { return (int) ((ticks % 1000) / 16.66); }
	public static boolean ticksIsAM(long ticks) { return ticksTo24Hours(ticks) < 12; }

	public static void nmsActionBar(Player player, String message) {
		String nmsver = SmoothSleep.nmsver;

		try {
			Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + nmsver + ".entity.CraftPlayer");
			Object craftPlayer = craftPlayerClass.cast(player);
			Object packet;
			Class<?> packetPlayOutChatClass = Class.forName("net.minecraft.server." + nmsver + ".PacketPlayOutChat");
			Class<?> packetClass = Class.forName("net.minecraft.server." + nmsver + ".Packet");
			Class<?> chatComponentTextClass = Class.forName("net.minecraft.server." + nmsver + ".ChatComponentText");
			Class<?> iChatBaseComponentClass = Class.forName("net.minecraft.server." + nmsver + ".IChatBaseComponent");
			try {
				Class<?> chatMessageTypeClass = Class.forName("net.minecraft.server." + nmsver + ".ChatMessageType");
				Object[] chatMessageTypes = chatMessageTypeClass.getEnumConstants();
				Object chatMessageType = null;
				for (Object obj : chatMessageTypes) { if (obj.toString().equals("GAME_INFO")) { chatMessageType = obj; } }
				Object chatCompontentText = chatComponentTextClass.getConstructor(new Class<?>[]{String.class}).newInstance(message);
				packet = packetPlayOutChatClass.getConstructor(new Class<?>[]{iChatBaseComponentClass, chatMessageTypeClass}).newInstance(chatCompontentText, chatMessageType);
			} catch (ClassNotFoundException cnfe) {
				Object chatCompontentText = chatComponentTextClass.getConstructor(new Class<?>[]{String.class}).newInstance(message);
				packet = packetPlayOutChatClass.getConstructor(new Class<?>[]{iChatBaseComponentClass, byte.class}).newInstance(chatCompontentText, (byte) 2);
			}
			Method craftPlayerHandleMethod = craftPlayerClass.getDeclaredMethod("getHandle");
			Object craftPlayerHandle = craftPlayerHandleMethod.invoke(craftPlayer);
			Field playerConnectionField = craftPlayerHandle.getClass().getDeclaredField("playerConnection");
			Object playerConnection = playerConnectionField.get(craftPlayerHandle);
			Method sendPacketMethod = playerConnection.getClass().getDeclaredMethod("sendPacket", packetClass);
			sendPacketMethod.invoke(playerConnection, packet);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static String trans(String s) {
		if (s == null) return null;
		s = translateAlternateColorCodes('&', s);
		s = translateHexColorCodes("&#", "", s);
		return s;
	}

	/**
	 * Calculates the night speed multiplier based on the curve amount and percent (0-1) of players sleeping.
	 * @param curve A value between 0-1 (not inclusive) that determines the curve. Higher values will cause the returned
	 *              value to increase more rapidy with lower percents and slower with higher percents, lower values will
	 *              cause the returned value to increase more slowly with lower percents and rise rapidy with higher percents.
	 *              A value of 0.5 will be a linear function where the percent is returned. Clamped 0-1
	 * @param percent The percent of sleeping players. 0 will always return the minimum night speed, and 1 will always
	 *                return the max night speed. Clamped 0-1
	 * @return Returns a value between 0-1 indicating how fast night should proceed.
	 */
	// Thanks to math wizard theminerdude AKA Drathares @ NarniaMC for helping to discover and simplify this equation
	public static double calcSpeed(double curve, double percent) {
		curve = curve > 1 ? 1 : curve < 0 ? 0 : curve;			// Clamp curve to 0-1.
		percent = percent > 1 ? 1 : percent < 0 ? 0 : percent;	// Clamp percent to 0-1;
		if (near(curve, 1)) return near(percent, 0) ? 0f : 1f;	// Filter out values too close to 1 -- any sleepers = max speed
		if (near(curve, 0)) return near(percent, 1) ? 1f : 0f;	// Filter out values too close to 0 -- all sleepers = max speed
		return (curve * percent) / (2 * curve * percent - curve - percent + 1);
	}
	public static boolean near(double a, double b) { return Math.abs(a - b) < 0.0001f; }

	public static StrSubstitutor sub(World w, Player p, int sc, int wc, double timescale, long ticksSlept, String nickname) {
		long worldTime = w.getTime();
		long timeLived = p.getTicksLived();
		AttributeInstance mli = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
		double maxLife = mli == null ? 20 : mli.getValue();
		Map<String, String> values = new LinkedHashMap<>(); // Linked to stay in order
		values.put("12H",				MiscUtils.ticksTo12Hours(worldTime) + "");
		values.put("24H",				String.format("%02d", MiscUtils.ticksTo24Hours(worldTime)) + "");
		values.put("MIN",				String.format("%02d", MiscUtils.ticksToMinutes(worldTime)));
		values.put("MER_UPPER",			MiscUtils.ticksIsAM(worldTime) ? "AM" : "PM");
		values.put("MER_LOWER",			MiscUtils.ticksIsAM(worldTime) ? "am" : "pm");
		values.put("SLEEPERS",			sc + "");
		values.put("WAKERS",			wc + "");
		values.put("TOTAL",				(sc+wc) + "");
		values.put("TIMESCALE",			String.format("%.2f", timescale));
		values.put("USERNAME",			p.getName());
		values.put("DISPLAYNAME",		p.getDisplayName());
		values.put("DISPLAYNAME_STRIP",	stripColor(p.getDisplayName()));
		values.put("HOURS_SLEPT",		(ticksSlept / 1000L) + "");
		values.put("LEVEL",				p.getLevel() + "");
		values.put("TIME_LIVED",		"{DAYS_LIVED}d, {REM_HOURS_LIVED}h, {REM_MINS_LIVED}m");
		values.put("DAYS_LIVED",		(timeLived / SmoothSleep.TICKS_PER_DAY) + "");
		values.put("REM_HOURS_LIVED",	((timeLived  % SmoothSleep.TICKS_PER_DAY) / SmoothSleep.TICKS_PER_HOUR) + "");
		values.put("REM_MINS_LIVED",	((timeLived % SmoothSleep.TICKS_PER_DAY % SmoothSleep.TICKS_PER_HOUR) / SmoothSleep.TICKS_PER_MIN) + "");
		values.put("TOTAL_HOURS_LIVED",	(p.getTicksLived() / SmoothSleep.TICKS_PER_HOUR) + "");
		values.put("TOTAL_MINS_LIVED",	(p.getTicksLived() / SmoothSleep.TICKS_PER_MIN) + "");
		values.put("WORLD",				w.getName());
		values.put("SERVER_IP",			Bukkit.getIp());
		values.put("SERVER_MOTD",		Bukkit.getMotd());
		values.put("SERVER_NAME",		Bukkit.getServer().getName());
		values.put("SERVER_MOTD_STRIP",	stripColor(Bukkit.getMotd()));
		values.put("SERVER_NAME_STRIP",	stripColor(Bukkit.getServer().getName()));
		values.put("NICKNAME",			nickname);
		values.put("NICKNAME_STRIP",	stripColor(nickname));
		values.put("HEALTH",			String.format("%d", (int) p.getHealth()));
		values.put("HEALTH_PER",		String.format("%d%%", (int)(p.getHealth() / maxLife * 100) ));
		values.put("HEALTH_BAR",		bar((int) p.getHealth(), (int) maxLife, 20));
		values.put("FOOD",				String.format("%d", p.getFoodLevel()));
		values.put("FOOD_PER",			String.format("%d%%", (int) (p.getFoodLevel() / 20.0 * 100)));
		values.put("FOOD_BAR",			bar(p.getFoodLevel(), 20, 20));
		return new StrSubstitutor(values, "{", "}");
	}

	private static String bar(int val, int size, int seg) {
		char fillSym = '\u28FF';
		int filled = (int) remapValue(true, 0, size, 0, seg, val);
		int remain = seg - filled;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < filled; i++) { sb.append(fillSym); }
		sb.append(ChatColor.BLACK);
		for (int i = 0; i < remain; i++) { sb.append(fillSym); }
		sb.append(ChatColor.RESET);
		return sb.toString();
	}

	public static void filterTrace(Exception ex, String filter) {
		SmoothSleep.logDebug("===== Filtered Stack Trace (" + ex.getMessage() + ") =====");
		for (StackTraceElement ste : ex.getStackTrace()) {
			if (filter == null || filter.isEmpty() || ste.getClassName().contains(filter)) {
				SmoothSleep.logDebug(String.format("  at %s.%s(%s:%d)", ste.getClassName(), ste.getMethodName(), ste.getFileName(), ste.getLineNumber()));
			}
		}
		SmoothSleep.logDebug("======================================================");
	}

	/*
	 * Modified from Elementeral's method on SpigotMC forums
	 * https://www.spigotmc.org/threads/hex-color-code-translate.449748/#post-3867804
	 */
	public static String translateHexColorCodes(String startTag, String endTag, String message) {

		StringBuffer buffer = new StringBuffer(message.length() + 4 * 8);

		final Pattern fullHex = Pattern.compile(startTag + "([A-Fa-f0-9]{6})" + endTag);
		Matcher fullMatcher = fullHex.matcher(message);
		while (fullMatcher.find()) {
			char[] group = fullMatcher.group(1).toCharArray();
			fullMatcher.appendReplacement(buffer,
					String.format("%1$sx%1$s%2$s%1$s%3$s%1$s%4$s%1$s%5$s%1$s%6$s%1$s%7$s",
						COLOR_CHAR, group[0], group[1], group[2], group[3], group[4], group[5])
			);
		}
		message = fullMatcher.appendTail(buffer).toString();

		buffer = new StringBuffer(message.length() + 4 * 8);
		final Pattern halfHex = Pattern.compile(startTag + "([A-Fa-f0-9]{3})" + endTag);
		Matcher halfMatcher = halfHex.matcher(message);
		while(halfMatcher.find()) {
			char[] group = halfMatcher.group(1).toCharArray();
			halfMatcher.appendReplacement(buffer,
					String.format("%1$sx%1$s%2$s%1$s%2$s%1$s%3$s%1$s%3$s%1$s%4$s%1$s%4$s",
						COLOR_CHAR, group[0], group[1], group[2])
			);
		}
		halfMatcher.appendTail(buffer);

		return buffer.toString();
	}

}
