package io.github.gaming32.chunkpregen.canyon;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static io.github.gaming32.chunkpregen.WorldArgumentType.getWorld;
import static io.github.gaming32.chunkpregen.WorldArgumentType.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class ChunkPregenPlugin extends JavaPlugin {
    public static final Logger LOGGER = Logger.getLogger("Chunk-Pregen");
    public static final CommandDispatcher<CommandSender> DISPATCHER = new CommandDispatcher<>();

    private final Set<Integer> tasks = new HashSet<>();

    @Override
    public void onEnable() {
        getCommand("pregen").setUsage(String.join("\n", tree(
            DISPATCHER.register(LiteralArgumentBuilder.<CommandSender>literal("pregen")
                .then(LiteralArgumentBuilder.<CommandSender>literal("generate")
                    .then(RequiredArgumentBuilder.<CommandSender, World>argument("world", world())
                        .then(RequiredArgumentBuilder.<CommandSender, Integer>argument("minX", integer())
                            .then(RequiredArgumentBuilder.<CommandSender, Integer>argument("minZ", integer())
                                .then(RequiredArgumentBuilder.<CommandSender, Integer>argument("maxX", integer())
                                    .then(RequiredArgumentBuilder.<CommandSender, Integer>argument("maxZ", integer())
                                        .executes(this::pregenerateChunks)
                                    )
                                )
                            )
                        )
                    )
                )
                .then(LiteralArgumentBuilder.<CommandSender>literal("listTasks")
                    .executes(ctx -> {
                        if (tasks.size() == 0) {
                            ctx.getSource().sendMessage("There are no running tasks");
                        } else {
                            ctx.getSource().sendMessage(
                                "There are " + tasks.size() + " running: " +
                                tasks.stream().map(Object::toString).collect(Collectors.joining(", "))
                            );
                        }
                        return 0;
                    })
                )
                .then(LiteralArgumentBuilder.<CommandSender>literal("cancel")
                    .then(RequiredArgumentBuilder.<CommandSender, Integer>argument("task", integer())
                        .executes(ctx -> {
                            int task = getInteger(ctx, "task");
                            if (!tasks.contains(task)) {
                                ctx.getSource().sendMessage(
                                    ChatColor.RED + "Sorry, " + task +
                                    " is not a running task ID."
                                );
                                return 0;
                            }
                            tasks.remove(task);
                            Bukkit.getScheduler().cancelTask(task);
                            return 0;
                        })
                    )
                )
            )
        )));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String input = label + (args.length > 0 ? (" " + String.join(" ", args)) : "");
        try {
            DISPATCHER.execute(input, sender);
        } catch (CommandSyntaxException e) {
            return false;
        }
        return true;
    }

    private int pregenerateChunks(CommandContext<CommandSender> ctx) {
        final int TASK_ID = 0, ITERATION = 1, CUR_X = 2, CUR_Z = 3;
        if (!ctx.getSource().isOp()) {
            ctx.getSource().sendMessage(ChatColor.RED + "Sorry, you don't have permission to pregenerate chunks.");
            return 0;
        }
        World world = getWorld(ctx, "world");
        int minX = floorToMultipleOf(getInteger(ctx, "minX"), 16) >> 2;
        int minZ = floorToMultipleOf(getInteger(ctx, "minZ"), 16) >> 2;
        int maxX = ceilToMultipleOf(getInteger(ctx, "maxX"), 16) >> 2;
        int maxZ = ceilToMultipleOf(getInteger(ctx, "maxZ"), 16) >> 2;
        long toGenerate = (long)(maxX - minX + 1) * (long)(maxZ - minZ + 1);
        long start = System.currentTimeMillis();
        int[] state = {-1, 0, minX, minZ};
        long[] generatedSoFar = {0};
        tasks.add(state[TASK_ID] = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            boolean finished = true;
            int[][] toUnload = new int[300][2];
            for (int i = 0; i < 300; i++) {
                finished = false;
                int[] unloadSnapshot = toUnload[i];
                world.loadChunk(
                    unloadSnapshot[0] = state[CUR_X],
                    unloadSnapshot[1] = state[CUR_Z],
                    true
                );
                if (++state[CUR_X] > maxX) {
                    state[CUR_X] = minX;
                    if (++state[CUR_Z] > maxZ) {
                        finished = true;
                        break;
                    }
                }
            }
            for (int[] unloadSnapshot : toUnload) {
                world.unloadChunk(unloadSnapshot[0], unloadSnapshot[1], true, true);
            }
            if (finished) {
                long end = System.currentTimeMillis();
                tasks.remove(state[TASK_ID]);
                Bukkit.getScheduler().cancelTask(state[TASK_ID]);
                sendMessage(ctx.getSource(), ChatColor.GREEN +
                    "Task " + state[TASK_ID] + ": Finished generating " + toGenerate + " chunks in " +
                    (end - start) / 1000.0 + " seconds"
                );
                return;
            }
            generatedSoFar[0] += 300;
            if (state[ITERATION]++ % 2 == 1) {
                sendMessage(ctx.getSource(), ChatColor.GOLD +
                    "Task " + state[TASK_ID] + ": Generated " + generatedSoFar[0] + "/" + toGenerate + " chunks"
                );
            }
        }, 0, 15));
        sendMessage(
            ctx.getSource(),
            "Starting chunk pregen with task ID " + state[TASK_ID] + ". Prepare for major lag!"
        );
        return 0;
    }

    @Override
    public void onDisable() {
    }

    private static void sendMessage(CommandSender sender, String message) {
        if (!sender.isOp()) {
            sender.sendMessage(message);
        }
        Bukkit.broadcast("[" + sender.getName() + "] " + message, Server.BROADCAST_CHANNEL_ADMINISTRATIVE);
    }

    private static List<String> tree(CommandNode<CommandSender> root) {
        var children = root.getChildren();
        List<String> lines = new ArrayList<>();
        if (children.size() == 1) {
            StringBuilder result = new StringBuilder();
            do {
                var child = children.iterator().next();
                result.append(child.getCommand() != null ? ChatColor.GREEN : ChatColor.WHITE)
                    .append(child.getUsageText())
                    .append(' ');
                children = child.getChildren();
            } while (children.size() == 1);
            result.setLength(result.length() - 1);
            lines.add(result.toString());
        }
        for (var child : children) {
            lines.add((child.getCommand() != null ? ChatColor.GREEN : ChatColor.WHITE) + child.getUsageText());
            var subChildren = child.getChildren();
            if (subChildren.size() == 1) {
                var subChild = subChildren.iterator().next();
                lines.set(
                    lines.size() - 1, lines.get(lines.size() - 1) + ' ' +
                    (subChild.getCommand() != null ? ChatColor.GREEN : ChatColor.WHITE) + subChild.getUsageText() + ' ' +
                    String.join("\n", tree(subChild))
                );
            } else {
                lines.addAll(tree(child).stream().map(line -> "   " + line).toList());
            }
        }
        return lines;
    }

    private static int floorToMultipleOf(int n, int m) {
        return (int)Math.floor(n / (double)m) * m;
    }

    private static int ceilToMultipleOf(int n, int m) {
        return (int)Math.ceil(n / (double)m) * m;
    }
}
