package com.fineedge.stonyshore.audit;

import java.io.IOException;
import java.util.function.BiConsumer;

/** An unsupported runtime provider must not discard unrelated audit evidence. */
final class AuditSections {
    @FunctionalInterface interface Action { void run() throws IOException; }
    private AuditSections() {}
    static void run(String name, Action action, BiConsumer<String, RuntimeException> onFailure) throws IOException {
        try { action.run(); }
        catch (RuntimeException ex) { onFailure.accept(name, ex); }
    }
}
