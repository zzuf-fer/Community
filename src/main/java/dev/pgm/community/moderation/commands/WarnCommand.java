package dev.pgm.community.moderation.commands;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import dev.pgm.community.CommunityCommand;
import dev.pgm.community.CommunityPermissions;
import dev.pgm.community.feature.FeatureManager;
import dev.pgm.community.moderation.feature.ModerationFeature;
import dev.pgm.community.moderation.punishments.PunishmentType;
import dev.pgm.community.users.feature.UsersFeature;
import dev.pgm.community.utils.CommandAudience;

public class WarnCommand extends CommunityCommand {

  private final ModerationFeature moderation;
  private final UsersFeature usernames;

  public WarnCommand(FeatureManager features) {
    this.moderation = features.getModeration();
    this.usernames = features.getUsers();
  }

  @CommandMethod("warn|w [target] [reason]")
  @CommandDescription("Warn a player for bad behavior")
  @CommandPermission(CommunityPermissions.WARN)
  public void warn(
      CommandAudience audience,
      @Argument("target") String target,
      @Argument("reason") String reason) {
    getTarget(target, usernames)
        .thenAccept(
            id -> {
              if (id.isPresent()) {
                moderation.punish(
                    PunishmentType.WARN,
                    id.get(),
                    audience,
                    reason,
                    null,
                    false,
                    isDisguised(audience));
              } else {
                audience.sendWarning(formatNotFoundComponent(target));
              }
            });
  }
}
