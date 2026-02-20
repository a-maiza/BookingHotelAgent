package com.cirta.bookinghotelagent.ai.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionMemoryStore implements ChatMemoryStore {

    private final Map<String, List<ChatMessage>> store = new ConcurrentHashMap<>();

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return store.getOrDefault(String.valueOf(memoryId), new ArrayList<>());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        store.put(String.valueOf(memoryId), new ArrayList<>(messages));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        store.remove(String.valueOf(memoryId));
    }
}
