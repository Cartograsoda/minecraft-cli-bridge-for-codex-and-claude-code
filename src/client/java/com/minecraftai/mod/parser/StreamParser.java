package com.minecraftai.mod.parser;

import java.util.function.Consumer;

public interface StreamParser {
    void parseLine(String line, Consumer<AgentEvent> eventConsumer);
    void reset();
}
