package dev.pgm.community.commands.graph;

import static net.kyori.adventure.text.Component.text;
import static tc.oc.pgm.util.text.TextException.noPermission;
import static tc.oc.pgm.util.text.TextException.playerOnly;
import static tc.oc.pgm.util.text.TextException.unknown;
import static tc.oc.pgm.util.text.TextException.usage;

import cloud.commandframework.annotations.AnnotationParser;
import cloud.commandframework.annotations.injection.ParameterInjector;
import cloud.commandframework.annotations.injection.ParameterInjectorRegistry;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.arguments.parser.ParserParameters;
import cloud.commandframework.arguments.parser.ParserRegistry;
import cloud.commandframework.arguments.parser.StandardParameters;
import cloud.commandframework.arguments.standard.StringArgument;
import cloud.commandframework.bukkit.CloudBukkitCapabilities;
import cloud.commandframework.exceptions.ArgumentParseException;
import cloud.commandframework.exceptions.CommandExecutionException;
import cloud.commandframework.exceptions.InvalidCommandSenderException;
import cloud.commandframework.exceptions.InvalidSyntaxException;
import cloud.commandframework.exceptions.NoPermissionException;
import cloud.commandframework.exceptions.parsing.ParserException;
import cloud.commandframework.execution.CommandExecutionCoordinator;
import cloud.commandframework.extra.confirmation.CommandConfirmationManager;
import cloud.commandframework.keys.CloudKey;
import cloud.commandframework.keys.SimpleCloudKey;
import cloud.commandframework.meta.CommandMeta;
import cloud.commandframework.minecraft.extras.MinecraftHelp;
import cloud.commandframework.paper.PaperCommandManager;
import dev.pgm.community.Community;
import dev.pgm.community.assistance.commands.PlayerHelpCommand;
import dev.pgm.community.assistance.commands.ReportCommands;
import dev.pgm.community.broadcast.BroadcastCommand;
import dev.pgm.community.chat.management.ChatManagementCommand;
import dev.pgm.community.commands.CommunityPluginCommand;
import dev.pgm.community.commands.ContainerCommand;
import dev.pgm.community.commands.FlightCommand;
import dev.pgm.community.commands.GamemodeCommand;
import dev.pgm.community.commands.ServerInfoCommand;
import dev.pgm.community.commands.StaffCommand;
import dev.pgm.community.commands.SudoCommand;
import dev.pgm.community.commands.providers.CommandAudienceProvider;
import dev.pgm.community.feature.FeatureManager;
import dev.pgm.community.freeze.FreezeCommand;
import dev.pgm.community.friends.commands.FriendshipCommand;
import dev.pgm.community.mobs.MobCommand;
import dev.pgm.community.moderation.commands.BanCommand;
import dev.pgm.community.moderation.commands.KickCommand;
import dev.pgm.community.moderation.commands.MuteCommand;
import dev.pgm.community.moderation.commands.PunishmentCommand;
import dev.pgm.community.moderation.commands.ToolCommand;
import dev.pgm.community.mutations.commands.MutationCommands;
import dev.pgm.community.nick.commands.NickCommands;
import dev.pgm.community.party.MapPartyCommands;
import dev.pgm.community.requests.commands.RequestCommands;
import dev.pgm.community.requests.commands.SponsorCommands;
import dev.pgm.community.requests.commands.TokenCommands;
import dev.pgm.community.teleports.TeleportCommand;
import dev.pgm.community.translations.TranslationCommand;
import dev.pgm.community.users.commands.UserInfoCommands;
import dev.pgm.community.utils.CommandAudience;
import io.leangen.geantyref.TypeFactory;
import io.leangen.geantyref.TypeToken;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.util.ComponentMessageThrowable;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.map.MapInfo;
import tc.oc.pgm.api.map.MapLibrary;
import tc.oc.pgm.api.map.MapOrder;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchManager;
import tc.oc.pgm.api.party.Party;
import tc.oc.pgm.api.party.VictoryCondition;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.setting.SettingKey;
import tc.oc.pgm.api.setting.SettingValue;
import tc.oc.pgm.classes.PlayerClass;
import tc.oc.pgm.command.injectors.AudienceProvider;
import tc.oc.pgm.command.injectors.MatchPlayerProvider;
import tc.oc.pgm.command.injectors.MatchProvider;
import tc.oc.pgm.command.injectors.TeamModuleInjector;
import tc.oc.pgm.command.parsers.DurationParser;
import tc.oc.pgm.command.parsers.MapInfoParser;
import tc.oc.pgm.command.parsers.MapPoolParser;
import tc.oc.pgm.command.parsers.MatchPlayerParser;
import tc.oc.pgm.command.parsers.PartyParser;
import tc.oc.pgm.command.parsers.PlayerClassParser;
import tc.oc.pgm.command.parsers.SettingKeyParser;
import tc.oc.pgm.command.parsers.SettingValueParser;
import tc.oc.pgm.command.parsers.TeamParser;
import tc.oc.pgm.command.parsers.TeamsParser;
import tc.oc.pgm.command.parsers.VictoryConditionParser;
import tc.oc.pgm.command.util.ParserBuilder;
import tc.oc.pgm.rotation.pools.MapPool;
import tc.oc.pgm.teams.Team;
import tc.oc.pgm.teams.TeamMatchModule;
import tc.oc.pgm.util.Audience;

public class CommunityCommandGraph {

  public static final CloudKey<LinkedList<String>> INPUT_QUEUE =
      SimpleCloudKey.of("_pgm_input_queue_", new TypeToken<LinkedList<String>>() {});

  private final Community community;
  private final FeatureManager features;
  private final PaperCommandManager<CommandSender> manager;
  private final MinecraftHelp<CommandSender> minecraftHelp;
  private final CommandConfirmationManager<CommandSender> confirmationManager;
  private final AnnotationParser<CommandSender> annotationParser;

  private final ParameterInjectorRegistry<CommandSender> injectors;
  private final ParserRegistry<CommandSender> parsers;

  public CommunityCommandGraph(Community community) throws Exception {
    this.community = community;
    this.features = community.getFeatures();
    this.manager =
        PaperCommandManager.createNative(
            community, CommandExecutionCoordinator.simpleCoordinator());

    //
    // Create the Minecraft help menu system
    //
    this.minecraftHelp = new MinecraftHelp<>("/communityhelp", Audience::get, manager);

    // Register Brigadier mappings
    if (this.manager.hasCapability(CloudBukkitCapabilities.BRIGADIER))
      this.manager.registerBrigadier();

    // Register asynchronous completions
    if (this.manager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION))
      this.manager.registerAsynchronousCompletions();

    // Add the input queue to the context, this allows greedy suggestions to work on it
    this.manager.registerCommandPreProcessor(
        context -> context.getCommandContext().store(INPUT_QUEUE, context.getInputQueue()));

    // By default, suggestions run by a filtered processor.
    // That prevents valid suggestions like "s" -> "Something" or "someh" -> "Something"
    this.manager.commandSuggestionProcessor((cpc, strings) -> strings);

    // Create the confirmation this.manager. This allows us to require certain commands to be
    // confirmed before they can be executed
    this.confirmationManager =
        new CommandConfirmationManager<>(
            30L,
            TimeUnit.SECONDS,
            // TODO: clickable
            context ->
                Audience.get(context.getCommandContext().getSender())
                    .sendWarning(text("Confirmation required. Confirm using /community confirm.")),
            sender ->
                Audience.get(sender).sendWarning(text("You don't have any pending commands.")));
    this.confirmationManager.registerConfirmationProcessor(this.manager);

    final Function<ParserParameters, CommandMeta> commandMetaFunction =
        p ->
            CommandMeta.simple()
                .with(
                    CommandMeta.DESCRIPTION,
                    p.get(StandardParameters.DESCRIPTION, "No description"))
                .build();

    this.annotationParser =
        new AnnotationParser<>(manager, CommandSender.class, commandMetaFunction);

    // Utility
    this.injectors = manager.parameterInjectorRegistry();
    this.parsers = manager.parserRegistry();

    setupExceptionHandlers();
    setupInjectors();
    setupParsers();
    registerCommands();

    manager.command(
        manager
            .commandBuilder("community")
            .literal("confirm")
            .meta(CommandMeta.DESCRIPTION, "Confirm a pending command")
            .handler(this.confirmationManager.createConfirmationExecutionHandler()));

    manager.command(
        manager
            .commandBuilder("communityhelp")
            .argument(StringArgument.optional("query", StringArgument.StringMode.GREEDY))
            .handler(
                context ->
                    minecraftHelp.queryCommands(
                        context.<String>getOptional("query").orElse(""), context.getSender())));
  }

  //
  // Commands
  //
  public void registerCommands() {
    register(new PlayerHelpCommand(features));
    register(new ReportCommands(features));
    register(new BroadcastCommand(features));
    register(new ChatManagementCommand(features));
    register(new CommunityPluginCommand(features));
    register(new ContainerCommand());
    register(new FlightCommand());
    register(new GamemodeCommand());
    register(new ServerInfoCommand());
    register(new StaffCommand());
    register(new SudoCommand());
    register(new FreezeCommand(features));
    register(new FriendshipCommand(features));
    register(new MobCommand(features));
    register(new BanCommand(features));
    register(new KickCommand(features));
    register(new MuteCommand(features));
    register(new PunishmentCommand(features));
    register(new ToolCommand(features));
    register(new MutationCommands(features));
    register(new NickCommands(features));
    register(new MapPartyCommands(features));
    register(new TokenCommands(features));
    register(new RequestCommands(features));
    register(new SponsorCommands(features));
    register(new TeleportCommand(features));
    register(new TranslationCommand(features));
    register(new UserInfoCommands(features));
  }

  public void register(Object command) {
    annotationParser.parse(command);
  }

  //
  // Injectors
  //
  protected void setupInjectors() {
    // PGM
    registerInjector(MatchManager.class, PGM::getMatchManager);
    registerInjector(MapLibrary.class, PGM::getMapLibrary);
    registerInjector(MapOrder.class, PGM::getMapOrder);

    registerInjector(Audience.class, new AudienceProvider());
    registerInjector(Match.class, new MatchProvider());
    registerInjector(MatchPlayer.class, new MatchPlayerProvider());
    registerInjector(TeamMatchModule.class, new TeamModuleInjector());

    // Community
    registerInjector(CommandAudience.class, new CommandAudienceProvider());
  }

  private <T> void registerInjector(Class<T> type, ParameterInjector<CommandSender, T> provider) {
    injectors.registerInjector(type, provider);
  }

  private <T> void registerInjector(Class<T> type, Function<PGM, T> function) {
    registerInjector(type, (a, b) -> function.apply(PGM.get()));
  }

  //
  // Parsers
  //
  private void setupParsers() {
    // Cloud has a default duration parser, but time type is not optional
    registerParser(Duration.class, new DurationParser());
    registerParser(MatchPlayer.class, new MatchPlayerParser());
    registerParser(MapPool.class, new MapPoolParser());

    registerParser(MapInfo.class, MapInfoParser::new);
    registerParser(Party.class, PartyParser::new);
    registerParser(Team.class, TeamParser::new);
    registerParser(TypeFactory.parameterizedClass(Collection.class, Team.class), TeamsParser::new);
    registerParser(PlayerClass.class, PlayerClassParser::new);
    registerParser(
        TypeFactory.parameterizedClass(Optional.class, VictoryCondition.class),
        new VictoryConditionParser());
    registerParser(SettingKey.class, new SettingKeyParser());
    registerParser(SettingValue.class, new SettingValueParser());
  }

  private <T> void registerParser(Class<T> type, ArgumentParser<CommandSender, T> parser) {
    parsers.registerParserSupplier(TypeToken.get(type), op -> parser);
  }

  private <T> void registerParser(Class<T> type, ParserBuilder<T> parser) {
    parsers.registerParserSupplier(TypeToken.get(type), op -> parser.create(manager, op));
  }

  private <T> void registerParser(Type type, ArgumentParser<CommandSender, T> parser) {
    parsers.registerParserSupplier(TypeToken.get(type), op -> parser);
  }

  private <T> void registerParser(Type type, ParserBuilder<T> parser) {
    parsers.registerParserSupplier(TypeToken.get(type), op -> parser.create(manager, op));
  }

  //
  // Exception handling
  //
  private void setupExceptionHandlers() {
    registerExceptionHandler(InvalidSyntaxException.class, e -> usage(e.getCorrectSyntax()));
    registerExceptionHandler(InvalidCommandSenderException.class, e -> playerOnly());
    registerExceptionHandler(NoPermissionException.class, e -> noPermission());

    manager.registerExceptionHandler(ArgumentParseException.class, this::handleException);
    manager.registerExceptionHandler(CommandExecutionException.class, this::handleException);
  }

  private <E extends Exception> void registerExceptionHandler(
      Class<E> ex, Function<E, ComponentLike> toComponent) {
    manager.registerExceptionHandler(
        ex, (cs, e) -> Audience.get(cs).sendWarning(toComponent.apply(e)));
  }

  private <E extends Exception> void handleException(CommandSender cs, E e) {
    Audience audience = Audience.get(cs);
    Component message = getMessage(e);
    if (message != null) audience.sendWarning(message);
  }

  private @Nullable Component getMessage(Throwable t) {
    ComponentMessageThrowable messageThrowable = getParentCause(t, ComponentMessageThrowable.class);
    if (messageThrowable != null) return messageThrowable.componentMessage();

    ParserException parseException = getParentCause(t, ParserException.class);
    if (parseException != null) return text(parseException.getMessage());

    return unknown(t).componentMessage();
  }

  private <T> T getParentCause(Throwable t, Class<T> type) {
    if (t == null || type.isInstance(t)) return type.cast(t);
    return getParentCause(t.getCause(), type);
  }
}
