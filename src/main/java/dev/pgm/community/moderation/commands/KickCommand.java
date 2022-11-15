package dev.pgm.community.moderation.commands;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.Flag;
import cloud.commandframework.annotations.specifier.FlagYielding;
import dev.pgm.community.CommunityCommand;
import dev.pgm.community.CommunityPermissions;
import dev.pgm.community.feature.FeatureManager;
import dev.pgm.community.moderation.feature.ModerationFeature;
import dev.pgm.community.moderation.punishments.PunishmentType;
import dev.pgm.community.users.feature.UsersFeature;
import dev.pgm.community.utils.CommandAudience;

public class KickCommand extends CommunityCommand {

  private final ModerationFeature moderation;
  private final UsersFeature usernames;

  public KickCommand(FeatureManager features) {
    this.moderation = features.getModeration();
    this.usernames = features.getUsers();
  }

  @CommandMethod("kick|k <target> <reason>")
  @CommandDescription("Kick a player from the server")
  @CommandPermission(CommunityPermissions.KICK)
  public void kick(
      CommandAudience audience,
      @Argument("target") String target,
      @Argument("reason") @FlagYielding String reason,
      @Flag(value = "silent", aliases = "s") boolean silent,
      @Flag(value = "off-record", aliases = "o") boolean offRecord) {
    getTarget(target, usernames)
        .thenAccept(
            id -> {
              if (id.isPresent()) {
                moderation.punish(
                    PunishmentType.KICK,
                    id.get(),
                    audience,
                    reason,
                    null,
                    false,
                    isDisguised(audience) || silent);
              } else {
                audience.sendWarning(formatNotFoundComponent(target));
              }
            });
  }
}
