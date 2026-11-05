package com.ldd.agent.chat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ChatRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChatRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ChatSession createSession(CapabilityMode mode) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO chat_session (id, title, session_mode, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """, id, "新对话", mode.name(), Timestamp.from(now), Timestamp.from(now));
        return new ChatSession(id, "新对话", mode, null, now, now);
    }

    public Optional<ChatSession> findSession(String id) {
        return jdbcTemplate.query("""
                        SELECT id, title, session_mode, selected_capability_id, created_at, updated_at
                        FROM chat_session WHERE id = ?
                        """, this::mapSession, id)
                .stream().findFirst();
    }

    public List<ChatSession> findRecentSessions() {
        return jdbcTemplate.query("""
                SELECT id, title, session_mode, selected_capability_id, created_at, updated_at
                FROM chat_session
                ORDER BY updated_at DESC
                LIMIT 50
                """, this::mapSession);
    }

    public List<ChatMessage> findMessages(String sessionId) {
        return jdbcTemplate.query("""
                SELECT id, session_id, message_role, content, capability_id, created_at
                FROM chat_message
                WHERE session_id = ?
                ORDER BY created_at ASC
                """, this::mapMessage, sessionId);
    }

    public List<ChatMessage> findRecentMessages(String sessionId, int limit) {
        List<ChatMessage> messages = new ArrayList<>(jdbcTemplate.query("""
                SELECT id, session_id, message_role, content, capability_id, created_at
                FROM chat_message
                WHERE session_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """, this::mapMessage, sessionId, limit));
        Collections.reverse(messages);
        return messages;
    }

    public ChatMessage appendMessage(String sessionId, String role, String content, String capabilityId) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO chat_message (id, session_id, message_role, content, capability_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, sessionId, role, content, capabilityId, Timestamp.from(now));
        jdbcTemplate.update("UPDATE chat_session SET updated_at = ? WHERE id = ?", Timestamp.from(now), sessionId);
        return new ChatMessage(id, sessionId, role, content, capabilityId, now);
    }

    public void updateSessionAfterReply(String sessionId, String title, String capabilityId) {
        jdbcTemplate.update("""
                UPDATE chat_session
                SET title = ?, selected_capability_id = ?, updated_at = ?
                WHERE id = ?
                """, title, capabilityId, Timestamp.from(Instant.now()), sessionId);
    }

    public void deleteSession(String sessionId) {
        jdbcTemplate.update("DELETE FROM chat_session WHERE id = ?", sessionId);
    }

    private ChatSession mapSession(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ChatSession(
                resultSet.getString("id"),
                resultSet.getString("title"),
                CapabilityMode.valueOf(resultSet.getString("session_mode")),
                resultSet.getString("selected_capability_id"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private ChatMessage mapMessage(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ChatMessage(
                resultSet.getString("id"),
                resultSet.getString("session_id"),
                resultSet.getString("message_role"),
                resultSet.getString("content"),
                resultSet.getString("capability_id"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }
}
