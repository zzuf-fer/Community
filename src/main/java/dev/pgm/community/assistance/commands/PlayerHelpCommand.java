package dev.pgm.community.assistance.commands;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.specifier.Greedy;
import dev.pgm.community.CommunityCommand;
import dev.pgm.community.assistance.feature.AssistanceFeature;
import dev.pgm.community.feature.FeatureManager;
import dev.pgm.community.moderation.feature.ModerationFeature;
import dev.pgm.community.moderation.punishments.types.MutePunishment;
import dev.pgm.community.utils.CommandAudience;
import java.util.Optional;
import org.bukkit.entity.Player;

public class PlayerHelpCommand extends CommunityCommand {

  private AssistanceFeature assistance;
  private ModerationFeature moderation;

  public PlayerHelpCommand(FeatureManager features) {
    this.assistance = features.getReports();
    this.moderation = features.getModeration();
  }

  @CommandMethod("assistance|assist|helpop|helpme <reason>")
  @CommandDescription("Request help from staff members")
  public void assistanceCommand(
      CommandAudience viewer, Player player, @Argument("reason") @Greedy String reason) {
    Optional<MutePunishment> mute = moderation.getCachedMute(player.getUniqueId());
    if (mute.isPresent()) {
      viewer.sendWarning(mute.get().getChatMuteMessage());
      return;
    }

    if (!assistance.canRequest(player.getUniqueId())) {
      int cooldown = assistance.getCooldownSeconds(player.getUniqueId());
      if (cooldown > 0) {
        viewer.sendWarning(assistance.getCooldownMessage(player.getUniqueId()));
        return;
      }
    }
    assistance.assist(player, reason);
  }
}
