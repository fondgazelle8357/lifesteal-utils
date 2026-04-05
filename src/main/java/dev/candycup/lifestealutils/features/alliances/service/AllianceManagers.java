package dev.candycup.lifestealutils.features.alliances.service;

import dev.candycup.lifestealutils.features.alliances.models.Alliance;
import dev.candycup.lifestealutils.features.alliances.models.AllianceMember;
import dev.candycup.lifestealutils.features.alliances.models.AllianceType;
import dev.candycup.lifestealutils.gaia.AlliancesAPIClient;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class AllianceManagers {
   private static final AllianceManager MODERN = new ModernAllianceManager();
   private static final AllianceManager LOCAL = new LocalAllianceManager();
   private static volatile List<Alliance> cachedPlayerAlliances = List.of();

   private AllianceManagers() {
   }

   public static CompletableFuture<List<Alliance>> fetchPlayerAlliances() {
      CompletableFuture<List<Alliance>> localFuture = LOCAL.fetchPlayerAlliances().exceptionally(error -> List.of());
      CompletableFuture<List<Alliance>> modernFuture;
      CompletableFuture<List<Alliance>> modernInvitesFuture;

      try {
         modernFuture = MODERN.fetchPlayerAlliances().exceptionally(error -> List.of());
         modernInvitesFuture = AlliancesAPIClient.fetchPlayerInvites().exceptionally(error -> List.of());
      } catch (RuntimeException error) {
         modernFuture = CompletableFuture.completedFuture(List.of());
         modernInvitesFuture = CompletableFuture.completedFuture(List.of());
      }

      return modernFuture
              .thenCombine(modernInvitesFuture, (modern, invites) -> {
                 List<Alliance> result = new ArrayList<>(modern);
                 for (Alliance invite : invites) {
                    if (invite == null) {
                       continue;
                    }
                    boolean exists = result.stream().anyMatch(existing -> existing != null && existing.id().equals(invite.id()));
                    if (!exists) {
                       result.add(invite);
                    }
                 }
                 return result;
              })
              .thenCombine(localFuture, (modernAndInvites, local) -> {
                 List<Alliance> result = new ArrayList<>(modernAndInvites);
                 result.addAll(local);
                 return result;
              })
              .thenApply(AllianceManagers::cacheSnapshotAndReturn);
   }

   public static CompletableFuture<Alliance> createAlliance(AllianceType type, String name, String prefix, String color, String description, String motd) {
      return forType(type).createAlliance(name, prefix, color, description, motd).thenApply(alliance -> {
         if (alliance != null) {
            upsertCachedAlliance(alliance);
         }
         return alliance;
      });
   }

   public static CompletableFuture<Alliance> fetchAlliance(Alliance alliance) {
      if (alliance == null) {
         return CompletableFuture.completedFuture(null);
      }
      return forAlliance(alliance).fetchAlliance(alliance.id());
   }

   public static CompletableFuture<Alliance> updateAlliance(Alliance alliance, String name, String prefix, String color, String description, String motd) {
      if (alliance == null) {
         return CompletableFuture.completedFuture(null);
      }
      return forAlliance(alliance).updateAlliance(alliance.id(), name, prefix, color, description, motd).thenApply(updatedAlliance -> {
         if (updatedAlliance != null) {
            upsertCachedAlliance(updatedAlliance);
         }
         return updatedAlliance;
      });
   }

   public static CompletableFuture<Boolean> addMember(Alliance alliance, String uuid, String cachedName) {
      if (!canMutateAllianceMembers(alliance) || uuid == null || uuid.isBlank()) {
         return CompletableFuture.completedFuture(false);
      }
      return forAlliance(alliance).addMember(alliance.id(), uuid, cachedName);
   }

   public static CompletableFuture<Boolean> removeMember(Alliance alliance, String memberId) {
      if (alliance == null) {
         return CompletableFuture.completedFuture(false);
      }
      return forAlliance(alliance).removeMember(memberId).thenApply(success -> {
         if (success) {
            removeCachedMember(alliance.id(), memberId);
         }
         return success;
      });
   }

   public static CompletableFuture<Boolean> acceptInvitation(Alliance alliance) {
      if (alliance == null) {
         return CompletableFuture.completedFuture(false);
      }
      return forAlliance(alliance).acceptInvitation(alliance.id()).thenApply(success -> {
         if (success) {
            upsertCachedAlliance(alliance);
         }
         return success;
      });
   }

   public static CompletableFuture<Boolean> rejectInvitation(Alliance alliance) {
      if (alliance == null) {
         return CompletableFuture.completedFuture(false);
      }
      return forAlliance(alliance).rejectInvitation(alliance.id()).thenApply(success -> {
         if (success) {
            removeCachedAlliance(alliance.id());
         }
         return success;
      });
   }

   public static CompletableFuture<Boolean> deleteAlliance(Alliance alliance) {
      if (alliance == null) {
         return CompletableFuture.completedFuture(false);
      }
      return forAlliance(alliance).deleteAlliance(alliance.id()).thenApply(success -> {
         if (success) {
            removeCachedAlliance(alliance.id());
         }
         return success;
      });
   }

   public static List<Alliance> getCachedPlayerAlliancesSnapshot() {
      return cachedPlayerAlliances;
   }

   private static List<Alliance> cacheSnapshotAndReturn(List<Alliance> alliances) {
      List<Alliance> sanitized = new ArrayList<>();
      if (alliances != null) {
         for (Alliance alliance : alliances) {
            if (alliance != null) {
               sanitized.add(alliance);
            }
         }
      }

      List<Alliance> snapshot = List.copyOf(sanitized);
      cachedPlayerAlliances = snapshot;
      return snapshot;
   }

   private static void upsertCachedAlliance(Alliance alliance) {
      if (alliance == null) {
         return;
      }

      List<Alliance> updated = new ArrayList<>(cachedPlayerAlliances);
      for (int i = 0; i < updated.size(); i++) {
         Alliance existing = updated.get(i);
         if (existing != null && existing.id().equals(alliance.id())) {
            updated.set(i, alliance);
            cachedPlayerAlliances = List.copyOf(updated);
            return;
         }
      }

      updated.add(alliance);
      cachedPlayerAlliances = List.copyOf(updated);
   }

   private static void removeCachedAlliance(String allianceId) {
      if (allianceId == null || allianceId.isBlank()) {
         return;
      }

      List<Alliance> updated = new ArrayList<>(cachedPlayerAlliances);
      if (updated.removeIf(alliance -> alliance != null && allianceId.equals(alliance.id()))) {
         cachedPlayerAlliances = List.copyOf(updated);
      }
   }

   private static void removeCachedMember(String allianceId, String memberId) {
      if (allianceId == null || allianceId.isBlank() || memberId == null || memberId.isBlank()) {
         return;
      }

      List<Alliance> updated = new ArrayList<>(cachedPlayerAlliances);
      for (int i = 0; i < updated.size(); i++) {
         Alliance cachedAlliance = updated.get(i);
         if (cachedAlliance == null || !allianceId.equals(cachedAlliance.id())) {
            continue;
         }

         AllianceMember removedMember = cachedAlliance.members().stream()
                 .filter(member -> member != null && memberId.equals(member.id()))
                 .findFirst()
                 .orElse(null);
         if (removedMember == null) {
            return;
         }

         if (isCurrentPlayerUuid(removedMember.uuid())) {
            updated.remove(i);
         } else {
            List<AllianceMember> remainingMembers = cachedAlliance.members().stream()
                    .filter(member -> member != null && !memberId.equals(member.id()))
                    .toList();
            updated.set(i, new Alliance(
                    cachedAlliance.id(),
                    cachedAlliance.name(),
                    cachedAlliance.prefix(),
                    cachedAlliance.color(),
                    cachedAlliance.description(),
                    cachedAlliance.motd(),
                    cachedAlliance.ownedBy(),
                    remainingMembers,
                    cachedAlliance.createdAt(),
                    cachedAlliance.updatedAt(),
                    cachedAlliance.type()
            ));
         }

         cachedPlayerAlliances = List.copyOf(updated);
         return;
      }
   }

   private static boolean isCurrentPlayerUuid(String uuid) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player == null || uuid == null || uuid.isBlank()) {
         return false;
      }

      return normalizeUuid(uuid).equals(normalizeUuid(minecraft.player.getStringUUID()));
   }

   private static String normalizeUuid(String uuid) {
      if (uuid == null || uuid.isBlank()) {
         return "";
      }
      return uuid.replace("-", "").toLowerCase(Locale.ROOT);
   }

   private static AllianceManager forAlliance(Alliance alliance) {
      return forType(alliance.type());
   }

   private static boolean canMutateAllianceMembers(Alliance alliance) {
      if (alliance == null) {
         return false;
      }

      return !isUnsetAllianceValue(alliance.id())
              && !isUnsetAllianceValue(alliance.name())
              && !isUnsetAllianceValue(alliance.getDisplayName());
   }

   private static boolean isUnsetAllianceValue(String value) {
      if (value == null) {
         return true;
      }

      String trimmed = value.trim();
      return trimmed.isEmpty() || trimmed.equalsIgnoreCase("NotSet");
   }

   private static AllianceManager forType(AllianceType type) {
      if (type == AllianceType.LOCAL) {
         return LOCAL;
      }
      return MODERN;
   }
}
