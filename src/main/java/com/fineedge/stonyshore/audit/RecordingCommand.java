package com.fineedge.stonyshore.audit;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

final class RecordingCommand {
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("stonyshore").requires(s->s.hasPermission(2))
            .then(Commands.literal("record")
                .then(Commands.literal("start").executes(c->{
                    GenerationRecording.start(c.getSource().getLevel());
                    c.getSource().sendSuccess(()->Component.literal("Shore recording started; previous session replaced. Explore new chunks, then /stonyshore record stop and /stonyshore audit."),false);return 1;
                }))
                .then(Commands.literal("stop").executes(c->{
                    GenerationRecording.stop(c.getSource().getLevel());
                    c.getSource().sendSuccess(()->Component.literal("Shore recording stopped. Retained samples will be included in /stonyshore audit."),false);return 1;
                }))
                .then(Commands.literal("status").executes(c->{
                    var snapshot=GenerationRecording.snapshot(c.getSource().getLevel());
                    int count=snapshot.has("chunks")?snapshot.getAsJsonArray("chunks").size():0;
                    c.getSource().sendSuccess(()->Component.literal("Shore recording: "+(GenerationRecording.active(c.getSource().getLevel())?"active":"inactive")+"; "+count+" retained chunk samples (limit 256)."),false);return 1;
                }))));
    }
    private RecordingCommand() {}
}
