package dev.candycup.lifestealutils.features.alliances;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.candycup.lifestealutils.Config;
import dev.candycup.lifestealutils.interapi.MessagingUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import java.util.concurrent.CompletableFuture;

public final class AllianceCommandController {
   private static String lastProvidedListNameThisSession = "";

   private AllianceCommandController() {
   }

   public static CompletableFuture<Suggestions> suggestAllianceNames(String remaining, SuggestionsBuilder builder) {
      String needle = remaining == null ? "" : remaining.trim().toLowerCase();
      for (AllianceModels.AllianceRecord alliance : AllianceService.listAll()) {
         String name = alliance.data == null ? "" : alliance.data.name;
         if (name == null || name.isBlank()) {
            continue;
         }
         if (needle.isBlank() || name.toLowerCase().contains(needle)) {
            builder.suggest(name);
         }
      }
      return builder.buildFuture();
   }

   public static CompletableFuture<Suggestions> suggestSelectableAllianceNames(String remaining, SuggestionsBuilder builder) {
      String needle = remaining == null ? "" : remaining.trim().toLowerCase(Locale.ROOT);
      Set<String> seen = new HashSet<>();
      for (AllianceModels.AllianceRecord alliance : AllianceService.listEditable()) {
         String name = alliance == null || alliance.data == null ? "" : alliance.data.name;
         if (name == null || name.isBlank()) {
            continue;
         }
         String suggestion = name.trim();
         String lowered = suggestion.toLowerCase(Locale.ROOT);
         if ((needle.isBlank() || lowered.contains(needle)) && seen.add(lowered)) {
            builder.suggest(suggestion);
         }
      }
      return builder.buildFuture();
   }

   public static CompletableFuture<Suggestions> suggestListNames(String allianceName, String remaining, SuggestionsBuilder builder) {
      AllianceModels.AllianceRecord alliance = AllianceService.findByName(allianceName);
      if (alliance == null || alliance.data == null || alliance.data.lists == null) {
         return builder.buildFuture();
      }
      String needle = remaining == null ? "" : remaining.trim().toLowerCase();
      for (AllianceModels.AlliancePlayerList list : alliance.data.lists) {
         if (list.name == null || list.name.isBlank()) {
            continue;
         }
         if (needle.isBlank() || list.name.toLowerCase().contains(needle)) {
            builder.suggest(list.name);
         }
      }
      return builder.buildFuture();
   }

   public static CompletableFuture<Suggestions> suggestAllianceAndListTargets(String remaining, SuggestionsBuilder builder) {
      String needle = remaining == null ? "" : remaining.trim().toLowerCase(Locale.ROOT);
      for (AllianceModels.AllianceRecord alliance : AllianceService.listAll()) {
         if (alliance == null || alliance.data == null || alliance.data.name == null || alliance.data.name.isBlank()) {
            continue;
         }
         String allianceName = alliance.data.name.trim();
         if (alliance.data.lists == null) {
            continue;
         }
         for (AllianceModels.AlliancePlayerList list : alliance.data.lists) {
            if (list == null || list.name == null || list.name.isBlank()) {
               continue;
            }
            String combined = allianceName + "/" + list.name.trim();
            if (needle.isBlank() || combined.toLowerCase(Locale.ROOT).contains(needle)) {
               builder.suggest(combined);
            }
         }
      }
      return builder.buildFuture();
   }

   public static CompletableFuture<Suggestions> suggestOnlinePlayers(String remaining, SuggestionsBuilder builder) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft == null || minecraft.getConnection() == null) {
         return builder.buildFuture();
      }
      String needle = remaining == null ? "" : remaining.trim().toLowerCase(Locale.ROOT);
      for (PlayerInfo playerInfo : minecraft.getConnection().getOnlinePlayers()) {
         if (playerInfo == null) {
            continue;
         }
         String name;
         //? if >1.21.8 {
         name = playerInfo.getProfile().name();
         //?} else {
         /*name = playerInfo.getProfile().getName();
         *///?}
         if (name == null || name.isBlank()) {
            continue;
         }
         if (needle.isBlank() || name.toLowerCase(Locale.ROOT).contains(needle)) {
            builder.suggest(name);
         }
      }
      return builder.buildFuture();
   }

    public static int createAlliance(String name) {
       if (name == null || name.isBlank()) {
          MessagingUtils.showMiniMessage("<red>Alliance name cannot be empty.</red>");
          return 0;
       }
       String trimmed = name.trim();
       if (trimmed.length() > 32) {
          MessagingUtils.showMiniMessage("<red>Alliance name must be 32 characters or fewer.</red>");
          return 0;
       }
       AllianceModels.AllianceRecord existing = AllianceService.findByName(trimmed);
       if (existing != null) {
          MessagingUtils.showMiniMessage("<red>An alliance named <white>" + escape(trimmed) + "</white> already exists.</red>");
          return 0;
       }
       AllianceModels.AllianceRecord record = AllianceService.createLocal(trimmed);
       AllianceSyncManager.publishOrUpdateAsync(record);
       MessagingUtils.showMiniMessage("<green>Created alliance <white>" + escape(record.data.name) + "</white>.</green>");
       return 1;
    }

   public static int selectAllianceByName(String rawAllianceName) {
      String allianceName = rawAllianceName == null ? "" : rawAllianceName.trim();
      if (allianceName.isEmpty()) {
         MessagingUtils.showMiniMessage("<red>Please provide an alliance name.</red>");
         return 0;
      }

      AllianceModels.AllianceRecord selectedAlliance = resolveEditableAllianceByName(allianceName);
      if (selectedAlliance == null) {
         MessagingUtils.showMiniMessage("<red>No alliance matches <white>" + escape(allianceName) + "</white>.</red>");
         return 0;
      }

      Config.setSelectedAllianceId(selectedAlliance.clientId);
      MessagingUtils.showMiniMessage("<green>Selected alliance: <white>" + escape(selectedAlliance.data.name) + "</white>.</green>");
      return 1;
   }

   public static int addMemberToAlliance(String usernameOrUuid, String allianceName, String listNameOrNull) {
      AllianceModels.AllianceRecord alliance = AllianceService.findByName(allianceName);
      if (alliance == null) {
         MessagingUtils.showMiniMessage("<red>No alliance matches <white>" + escape(allianceName) + "</white>.</red>");
         return 0;
      }
      if (!alliance.canEdit) {
         MessagingUtils.showMiniMessage("<red>You cannot add users to alliances you don't control! Create your own alliance if you wish to execute this command.</red>");
         return 0;
      }

      String listToUse = listNameOrNull;
      if (listToUse != null && !listToUse.isBlank()) {
         lastProvidedListNameThisSession = listToUse;
      } else {
         listToUse = lastProvidedListNameThisSession;
      }

      AllianceModels.AlliancePlayerList list = AllianceService.resolveList(alliance, listToUse);
      if (list == null) {
         MessagingUtils.showMiniMessage("<red>No previous list selected this session and no list provided.</red>");
         return 0;
      }

      String uuid = AllianceProfileCacheManager.resolveUuidFromInput(usernameOrUuid);
      if (uuid == null) {
         MessagingUtils.showMiniMessage("<red>Unable to resolve player <white>" + escape(usernameOrUuid) + "</white>.</red>");
         return 0;
      }

      boolean added = AllianceService.addMember(alliance, list.id, uuid);
      if (!added) {
         MessagingUtils.showMiniMessage("<yellow><white>" + escape(usernameOrUuid) + "</white> is already in <white>" + escape(alliance.data.name) + "</white>.</yellow>");
         return 0;
      }

      AllianceProfileCacheManager.cache(usernameOrUuid, uuid);
      AllianceSyncManager.publishOrUpdateAsync(alliance);
      MessagingUtils.showMiniMessage("<green>Added <white>" + escape(usernameOrUuid) + "</white> to <white>" + escape(alliance.data.name) + "</white>.</green>");
      return 1;
   }

   public static int addMemberToAllianceParsed(String usernameOrUuid, String allianceAndMaybeList) {
      ParsedAddTarget parsed = parseAddTarget(allianceAndMaybeList);
      if (parsed == null) {
         MessagingUtils.showMiniMessage("<red>Could not resolve alliance. Use <white>/lsu alliances list</white> and try again.</red>");
         return 0;
      }
      return addMemberToAlliance(usernameOrUuid, parsed.allianceName(), parsed.listName());
   }

   public static int removeMemberFromAlliance(String usernameOrUuid, String allianceName) {
      AllianceModels.AllianceRecord alliance = AllianceService.findByName(allianceName);
      if (alliance == null) {
         MessagingUtils.showMiniMessage("<red>No alliance matches <white>" + escape(allianceName) + "</white>.</red>");
         return 0;
      }
      if (!alliance.canEdit) {
         MessagingUtils.showMiniMessage("<red>You cannot remove users from alliances you don't control!</red>");
         return 0;
      }

      String uuid = AllianceProfileCacheManager.resolveUuidFromInput(usernameOrUuid);
      if (uuid == null) {
         MessagingUtils.showMiniMessage("<red>Unable to resolve player <white>" + escape(usernameOrUuid) + "</white>.</red>");
         return 0;
      }

      boolean removed = AllianceService.removeMember(alliance, uuid);
      if (!removed) {
         MessagingUtils.showMiniMessage("<red>Couldn't remove <white>" + escape(usernameOrUuid) + "</white> from <white>" + escape(alliance.data.name) + "</white>.</red>");
         return 0;
      }

      AllianceSyncManager.publishOrUpdateAsync(alliance);
      MessagingUtils.showMiniMessage("<green>Removed <white>" + escape(usernameOrUuid) + "</white> from <white>" + escape(alliance.data.name) + "</white>.</green>");
      return 1;
   }

   public static int removeMemberFromAllianceParsed(String usernameOrUuid, String allianceAndMaybeList) {
      ParsedAddTarget parsed = parseAddTarget(allianceAndMaybeList);
      if (parsed == null) {
         MessagingUtils.showMiniMessage("<red>Could not resolve alliance. Use <white>/lsu alliances list</white> and try again.</red>");
         return 0;
      }
      return removeMemberFromAlliance(usernameOrUuid, parsed.allianceName());
   }

   public static CompletableFuture<Suggestions> suggestCurrentAllianceMemberNames(String remaining, SuggestionsBuilder builder) {
      AllianceModels.AllianceRecord alliance = resolveSelectedEditableAlliance(false);
      if (alliance == null || alliance.data == null || alliance.data.lists == null) {
         return builder.buildFuture();
      }

      String needle = remaining == null ? "" : remaining.trim().toLowerCase(Locale.ROOT);
      Set<String> seen = new HashSet<>();
      for (AllianceModels.AlliancePlayerList list : alliance.data.lists) {
         if (list == null || list.members == null) {
            continue;
         }
         for (AllianceModels.AllianceMember member : list.members) {
            String suggestion = AllianceProfileCacheManager.displayNameForUuid(member == null ? null : member.uuid);
            if (suggestion == null || suggestion.isBlank()) {
               continue;
            }
            String lowered = suggestion.toLowerCase(Locale.ROOT);
            if ((needle.isBlank() || lowered.contains(needle)) && seen.add(lowered)) {
               builder.suggest(suggestion);
            }
         }
      }
      return builder.buildFuture();
   }

   public static int addSelectedAllianceMemberByName(String rawPlayerName) {
      String playerName = rawPlayerName == null ? "" : rawPlayerName.trim();
      if (playerName.isEmpty()) {
         MessagingUtils.showMiniMessage("<red>Please provide a player name.</red>");
         return 0;
      }

      AllianceModels.AllianceRecord alliance = resolveSelectedEditableAlliance(true);
      if (alliance == null) {
         return 0;
      }

      AllianceModels.AlliancePlayerList list = resolveQuickActionList(alliance);
      if (list == null) {
         MessagingUtils.showMiniMessage("<red>No usable list was found for <white>" + escape(alliance.data.name) + "</white>.</red>");
         return 0;
      }

      return addMemberToAlliance(playerName, alliance.data.name, list.id);
   }

   public static int removeSelectedAllianceMemberByName(String rawPlayerName) {
      String playerName = rawPlayerName == null ? "" : rawPlayerName.trim();
      if (playerName.isEmpty()) {
         MessagingUtils.showMiniMessage("<red>Please provide a player name.</red>");
         return 0;
      }

      AllianceModels.AllianceRecord alliance = resolveSelectedEditableAlliance(true);
      if (alliance == null) {
         return 0;
      }

      String uuid = resolveAllianceMemberUuid(alliance, playerName);
      if (uuid == null) {
         MessagingUtils.showMiniMessage("<red>No member named <white>" + escape(playerName) + "</white> was found in <white>" + escape(alliance.data.name) + "</white>.</red>");
         return 0;
      }

      String displayName = AllianceProfileCacheManager.displayNameForUuid(uuid);
      boolean removed = AllianceService.removeMember(alliance, uuid);
      if (!removed) {
         MessagingUtils.showMiniMessage("<red>Couldn't remove <white>" + escape(displayName) + "</white> from <white>" + escape(alliance.data.name) + "</white>.</red>");
         return 0;
      }

      AllianceSyncManager.publishOrUpdateAsync(alliance);
      MessagingUtils.showMiniMessage("<green>Removed <white>" + escape(displayName) + "</white> from <white>" + escape(alliance.data.name) + "</white>.</green>");
      return 1;
   }

   public static int listSelectedAllianceMembers() {
      AllianceModels.AllianceRecord alliance = resolveSelectedEditableAlliance(true);
      if (alliance == null) {
         return 0;
      }

      List<String> names = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      for (AllianceModels.AlliancePlayerList list : alliance.data.lists) {
         if (list == null || list.members == null) {
            continue;
         }
         for (AllianceModels.AllianceMember member : list.members) {
            String name = AllianceProfileCacheManager.displayNameForUuid(member == null ? null : member.uuid);
            if (name == null || name.isBlank()) {
               continue;
            }
            String lowered = name.toLowerCase(Locale.ROOT);
            if (seen.add(lowered)) {
               names.add(name);
            }
         }
      }

      names.sort(String.CASE_INSENSITIVE_ORDER);
      if (names.isEmpty()) {
         MessagingUtils.showMiniMessage("<yellow><white>" + escape(alliance.data.name) + "</white> has no members.</yellow>");
         return 1;
      }

      StringBuilder message = new StringBuilder("<green>")
              .append(escape(alliance.data.name))
              .append("</green><gray>: </gray>");
      for (int i = 0; i < names.size(); i++) {
         if (i > 0) {
            message.append("<gray>, </gray>");
         }
         message.append("<white>").append(escape(names.get(i))).append("</white>");
      }
      MessagingUtils.showMiniMessage(message.toString());
      return 1;
   }

   public static void toggleSelectedAllianceMember(String targetUuid, String targetName) {
      AllianceModels.AllianceRecord alliance = resolveSelectedEditableAlliance(true);
      if (alliance == null) {
         return;
      }

      String normalizedUuid = AllianceProfileCacheManager.normalizeUuid(targetUuid);
      if (normalizedUuid == null) {
         MessagingUtils.showMiniMessage("<red>Unable to resolve player <white>" + escape(targetName) + "</white>.</red>");
         return;
      }

      if (allianceContainsMember(alliance, normalizedUuid)) {
         boolean removed = AllianceService.removeMember(alliance, normalizedUuid);
         if (removed) {
            AllianceSyncManager.publishOrUpdateAsync(alliance);
            MessagingUtils.showMiniMessage("<green>Removed <white>" + escape(targetName) + "</white> from <white>" + escape(alliance.data.name) + "</white>.</green>");
         } else {
            MessagingUtils.showMiniMessage("<red>Couldn't remove <white>" + escape(targetName) + "</white> from <white>" + escape(alliance.data.name) + "</white>.</red>");
         }
         return;
      }

      AllianceModels.AlliancePlayerList list = resolveQuickActionList(alliance);
      if (list == null) {
         MessagingUtils.showMiniMessage("<red>No usable list was found for <white>" + escape(alliance.data.name) + "</white>.</red>");
         return;
      }

      AllianceProfileCacheManager.cache(targetName, normalizedUuid);
      boolean added = AllianceService.addMember(alliance, list.id, normalizedUuid);
      if (added) {
         AllianceSyncManager.publishOrUpdateAsync(alliance);
         MessagingUtils.showMiniMessage("<green>Added <white>" + escape(targetName) + "</white> to <white>" + escape(alliance.data.name) + "</white>.</green>");
      } else {
         MessagingUtils.showMiniMessage("<red>Couldn't add <white>" + escape(targetName) + "</white> to <white>" + escape(alliance.data.name) + "</white>.</red>");
      }
   }

   private static String escape(String input) {
      if (input == null) {
         return "";
      }
      return input.replace("<", "").replace(">", "");
   }

   private static AllianceModels.AllianceRecord resolveEditableAllianceByName(String allianceName) {
      AllianceModels.AllianceRecord alliance = AllianceService.findByName(allianceName);
      if (alliance == null || !alliance.canEdit) {
         return null;
      }
      return alliance;
   }

   private static AllianceModels.AllianceRecord resolveSelectedEditableAlliance(boolean notify) {
      String selectedAllianceId = Config.getSelectedAllianceId();
      if (selectedAllianceId == null || selectedAllianceId.isBlank()) {
         if (notify) {
            MessagingUtils.showMiniMessage("<red>We're not sure which alliance you want to use. Select one first with <white>/lsu alliances select <alliance name></white>.</red>");
         }
         return null;
      }

      AllianceModels.AllianceRecord alliance = AllianceService.findByClientId(selectedAllianceId);
      if (alliance == null) {
         Config.setSelectedAllianceId("");
         if (notify) {
            MessagingUtils.showMiniMessage("<red>Your selected alliance no longer exists. Select another with <white>/lsu alliances select <alliance name></white>.</red>");
         }
         return null;
      }
      if (!alliance.canEdit) {
         Config.setSelectedAllianceId("");
         if (notify) {
            MessagingUtils.showMiniMessage("<red>Your selected alliance can't be used for quick ally actions. Select one you can manage with <white>/lsu alliances select <alliance name></white>.</red>");
         }
         return null;
      }
      return alliance;
   }

   private static AllianceModels.AlliancePlayerList resolveQuickActionList(AllianceModels.AllianceRecord alliance) {
      AllianceModels.AlliancePlayerList resolved = AllianceService.resolveList(alliance, null);
      if (resolved != null) {
         return resolved;
      }
      if (alliance != null && alliance.data != null && alliance.data.lists != null && !alliance.data.lists.isEmpty()) {
         return alliance.data.lists.get(0);
      }
      return null;
   }

   private static String resolveAllianceMemberUuid(AllianceModels.AllianceRecord alliance, String playerNameOrUuid) {
      String normalizedQueryUuid = AllianceProfileCacheManager.normalizeUuid(playerNameOrUuid);
      String loweredQuery = playerNameOrUuid.toLowerCase(Locale.ROOT);
      for (AllianceModels.AlliancePlayerList list : alliance.data.lists) {
         if (list == null || list.members == null) {
            continue;
         }
         for (AllianceModels.AllianceMember member : list.members) {
            String memberUuid = AllianceProfileCacheManager.normalizeUuid(member == null ? null : member.uuid);
            if (memberUuid == null) {
               continue;
            }
            if (normalizedQueryUuid != null && memberUuid.equalsIgnoreCase(normalizedQueryUuid)) {
               return memberUuid;
            }
            String cachedName = AllianceProfileCacheManager.getCachedNameByUuid(memberUuid);
            if (cachedName != null && cachedName.toLowerCase(Locale.ROOT).equals(loweredQuery)) {
               return memberUuid;
            }
         }
      }
      return null;
   }

   private static boolean allianceContainsMember(AllianceModels.AllianceRecord alliance, String normalizedUuid) {
      return resolveAllianceMemberUuid(alliance, normalizedUuid) != null;
   }

   private static ParsedAddTarget parseAddTarget(String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }
      String input = raw.trim();

      int slashIndex = input.indexOf('/');
      if (slashIndex > 0 && slashIndex < input.length() - 1) {
         String allianceName = input.substring(0, slashIndex).trim();
         String listName = input.substring(slashIndex + 1).trim();
         if (!allianceName.isBlank()) {
            return new ParsedAddTarget(allianceName, listName.isBlank() ? null : listName);
         }
      }

      if (input.startsWith("\"") && input.length() > 1) {
         int closingQuote = input.indexOf('"', 1);
         if (closingQuote > 1) {
            String allianceName = input.substring(1, closingQuote).trim();
            String rest = input.substring(closingQuote + 1).trim();
            String listName = rest.isBlank() ? null : rest;
            if (!allianceName.isBlank()) {
               return new ParsedAddTarget(allianceName, listName);
            }
         }
      }

      ArrayList<String> candidateNames = new ArrayList<>();
      for (AllianceModels.AllianceRecord alliance : AllianceService.listAll()) {
         String name = alliance == null || alliance.data == null ? null : alliance.data.name;
         if (name != null && !name.isBlank()) {
            candidateNames.add(name.trim());
         }
      }
      candidateNames.sort((a, b) -> Integer.compare(b.length(), a.length()));

      String loweredInput = input.toLowerCase(Locale.ROOT);
      for (String candidate : candidateNames) {
         String lowered = candidate.toLowerCase(Locale.ROOT);
         if (Objects.equals(loweredInput, lowered)) {
            return new ParsedAddTarget(candidate, null);
         }
         if (loweredInput.startsWith(lowered + " ")) {
            String remainder = input.substring(candidate.length()).trim();
            return new ParsedAddTarget(candidate, remainder.isBlank() ? null : remainder);
         }
      }

      return new ParsedAddTarget(input, null);
   }

   private record ParsedAddTarget(String allianceName, String listName) {
   }
}
