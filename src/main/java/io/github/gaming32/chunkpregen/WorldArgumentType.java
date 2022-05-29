package io.github.gaming32.chunkpregen;

import java.util.Arrays;
import java.util.Collection;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import org.bukkit.Bukkit;
import org.bukkit.World;

public class WorldArgumentType implements ArgumentType<World> {
    private static final Collection<String> EXAMPLES = Arrays.asList("world", "world_nether");
    private static final DynamicCommandExceptionType EXCEPTION_TYPE = new DynamicCommandExceptionType(
        value -> new LiteralMessage("World not found '" + value + "'")
    );
    private static final WorldArgumentType ARG_TYPE = new WorldArgumentType();

    private WorldArgumentType() {
    }

    public static WorldArgumentType world() {
        return ARG_TYPE;
    }

    public static World getWorld(final CommandContext<?> context, final String name) {
        return context.getArgument(name, World.class);
    }

    @Override
    public World parse(final StringReader reader) throws CommandSyntaxException {
        String name = reader.readString();
        World world = Bukkit.getWorld(name);
        if (world == null) {
            throw EXCEPTION_TYPE.createWithContext(reader, name);
        }
        return world;
    }

    @Override
    public String toString() {
        return "world()";
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
