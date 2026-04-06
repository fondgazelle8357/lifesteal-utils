package dev.candycup.lifestealutils.features.alliances;

import dev.candycup.lifestealutils.Config;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public final class AllianceHitboxColorResolver {
   public static final int DEFAULT_COLOR = 0x55FF55;
   public static final String DEFAULT_COLOR_STRING = "#55FF55";

   private AllianceHitboxColorResolver() {
   }

   public static boolean shouldRenderInvisibleHitbox(Player player) {
      return resolveColor(player) != null;
   }

   public static Integer resolveColor(Player player) {
      try {
         if (player == null || !Config.isEnableAlliances() || !Config.isAllianceHitboxColorsEnabled()) {
            return null;
         }

         Minecraft client = Minecraft.getInstance();
         if (client.player == null || client.options == null) {
            return null;
         }

         if (player == client.player) {
            if (!Config.isShowOwnAllianceHitboxInThirdPerson()) {
               return null;
            }
            CameraType cameraType = client.options.getCameraType();
            if (cameraType == null || cameraType.isFirstPerson()) {
               return null;
            }
            return parseColorOrDefault(Config.getOwnAllianceHitboxColor(), DEFAULT_COLOR);
         }

         HitboxAllianceCandidate candidate = resolveHitboxCandidate(player.getStringUUID());
         if (candidate == null || candidate.allianceId() == null || candidate.allianceId().isBlank()) {
            return null;
         }

         int fallback = parseColorOrDefault(candidate.color(), DEFAULT_COLOR);
         String configuredColor = Config.getAllianceHitboxColorOverride(
                 candidate.allianceId(),
                 normalizeConfigColor(candidate.color())
         );
         return parseColorOrDefault(configuredColor, fallback);
      } catch (RuntimeException ignored) {
         return null;
      }
   }

   public static List<HitboxAllianceCandidate> getHitboxCandidates() {
      ArrayList<HitboxAllianceCandidate> candidates = new ArrayList<>();
      for (AllianceModels.AllianceRecord alliance : AllianceService.listAll()) {
         if (alliance == null || alliance.clientId == null || alliance.clientId.isBlank() || alliance.data == null) {
            continue;
         }
         candidates.add(new HitboxAllianceCandidate(
                 alliance.clientId,
                 alliance.data.name == null || alliance.data.name.isBlank() ? "Alliance" : alliance.data.name,
                 formatColor(safeColor(alliance.data.color))
         ));
      }
      return candidates;
   }

   public static HitboxAllianceCandidate resolveHitboxCandidate(String playerUuid) {
      String normalizedPlayerUuid = AllianceProfileCacheManager.normalizeUuid(playerUuid);
      if (normalizedPlayerUuid == null) {
         return null;
      }

      for (AllianceModels.AllianceRecord alliance : AllianceService.listAll()) {
         if (alliance == null || alliance.clientId == null || alliance.clientId.isBlank() || alliance.data == null || alliance.data.lists == null) {
            continue;
         }
         for (AllianceModels.AlliancePlayerList list : alliance.data.lists) {
            if (list == null || list.members == null) {
               continue;
            }
            for (AllianceModels.AllianceMember member : list.members) {
               String memberUuid = AllianceProfileCacheManager.normalizeUuid(member == null ? null : member.uuid);
               if (memberUuid == null || !memberUuid.equalsIgnoreCase(normalizedPlayerUuid)) {
                  continue;
               }
               return new HitboxAllianceCandidate(
                       alliance.clientId,
                       alliance.data.name == null || alliance.data.name.isBlank() ? "Alliance" : alliance.data.name,
                       formatColor(safeColor(alliance.data.color))
               );
            }
         }
      }
      return null;
   }

   public static String normalizeConfigColor(String rawColor) {
      Integer parsed = parseColor(rawColor);
      return parsed == null ? DEFAULT_COLOR_STRING : formatColor(parsed);
   }

   private static int safeColor(int color) {
      if (color < 0 || color > 0xFFFFFF) {
         return DEFAULT_COLOR;
      }
      return color;
   }

   private static int parseColorOrDefault(String rawColor, int fallback) {
      Integer parsed = parseColor(rawColor);
      return parsed == null ? fallback : parsed;
   }

   private static Integer parseColor(String rawColor) {
      if (rawColor == null) {
         return null;
      }

      String normalized = rawColor.trim();
      if (normalized.isEmpty()) {
         return null;
      }
      if (normalized.startsWith("<") && normalized.endsWith(">")) {
         normalized = normalized.substring(1, normalized.length() - 1);
      }
      if (normalized.startsWith("/")) {
         normalized = normalized.substring(1);
      }
      if (normalized.startsWith("#")) {
         normalized = normalized.substring(1);
      }
      if (normalized.length() != 6) {
         return null;
      }

      try {
         return Integer.parseInt(normalized, 16);
      } catch (NumberFormatException ignored) {
         return null;
      }
   }

   private static String formatColor(int color) {
      return String.format("#%06X", color & 0xFFFFFF);
   }

   public record HitboxAllianceCandidate(String allianceId, String displayName, String color) {
   }
}
