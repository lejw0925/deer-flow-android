package com.deerflow.mobile.data

import java.io.Closeable
import java.io.ByteArrayInputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class DeerFlowApiStreamingTest {
    @Test
    fun reconnectsWithLastEventIdAndSkipsDuplicateFrames() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                sse(
                    "event: metadata\nid: event-1\ndata: {\"run_id\":\"run-1\"}",
                    "event: messages-tuple\nid: event-2\ndata: {\"type\":\"ai\",\"id\":\"ai-1\",\"content\":\"Hello \"}",
                ),
                sse(
                    "event: messages-tuple\nid: event-2\ndata: {\"type\":\"ai\",\"id\":\"ai-1\",\"content\":\"Hello \"}",
                    "event: messages-tuple\nid: event-3\ndata: {\"type\":\"ai\",\"id\":\"ai-1\",\"content\":\"world\"}",
                    "event: end\nid: event-4\ndata: null",
                ),
            ),
        )
        try {
            val updates = mutableListOf<StreamUpdate>()
            val result = withTimeout(10_000) {
                DeerFlowApi(server.url, NoopSessionCookieStore).streamMessage(
                    threadId = "thread-1",
                    message = "Reconnect this run",
                    options = RunOptions(),
                ) { updates += it }
            }

            assertEquals(
                StreamResult.TerminalEnd(runId = "run-1", lastEventId = "event-4"),
                result,
            )
            assertEquals(2, server.requests.size)
            assertEquals("event-2", server.requests[1].headers["last-event-id"])
            assertEquals(
                listOf("Hello ", "world"),
                updates.filterIsInstance<StreamUpdate.MessageChunk>().map { it.value.text },
            )
            assertEquals(
                listOf("event-1", "event-2", "event-3", "event-4"),
                updates.filterIsInstance<StreamUpdate.EventId>().map { it.value },
            )
            assertTrue(updates.any { it == StreamUpdate.Reconnecting(1) })
            assertTrue(updates.last() == StreamUpdate.Finished)
        } finally {
            server.close()
        }
    }

    @Test
    fun initialResponseLossWithOnlyTheUserMessageStaysRetryable() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(closeWithoutResponse = true),
                ScriptedResponse(contentType = "application/json", body = "[]"),
            ),
        )
        try {
            val updates = mutableListOf<StreamUpdate>()
            val result = withTimeout(5_000) {
                DeerFlowApi(
                    server.url,
                    NoopSessionCookieStore,
                    StreamReconnectPolicy(maxInitialRecoveryAttempts = 1, reconnectDelayMs = 0),
                ).streamMessage(
                    threadId = "thread-1",
                    message = "Recover this run",
                    options = RunOptions(),
                    clientMessageId = "client-message-1",
                ) { updates += it }
            }

            assertEquals(StreamResult.RetryableDisconnect(null, null, 1), result)
            assertEquals(2, server.requests.size)
            assertEquals("POST", server.requests[0].method)
            assertEquals("/api/threads/thread-1/runs/stream", server.requests[0].path)
            assertEquals("GET", server.requests[1].method)
            assertEquals("/api/threads/thread-1/runs", server.requests[1].path)
            assertEquals(listOf(1), updates.filterIsInstance<StreamUpdate.Reconnecting>().map { it.attempt })
        } finally {
            server.close()
        }
    }

    @Test
    fun initialResponseLossWithTerminalRunReturnsTerminalEnd() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(closeWithoutResponse = true),
                ScriptedResponse(
                    contentType = "application/json",
                    body = """[{"run_id":"run-1","status":"timeout","stop_reason":"Gateway deadline elapsed"}]""",
                ),
            ),
        )
        try {
            val result = DeerFlowApi(
                server.url,
                NoopSessionCookieStore,
                StreamReconnectPolicy(reconnectDelayMs = 0),
            ).streamMessage(
                threadId = "thread-1",
                message = "Recover terminal run",
                options = RunOptions(),
            ) { }

            assertEquals(
                StreamResult.TerminalEnd("run-1", null, GatewayRunStatus.Timeout),
                result,
            )
            assertEquals(2, server.requests.size)
            assertEquals("/api/threads/thread-1/runs", server.requests[1].path)
        } finally {
            server.close()
        }
    }

    @Test
    fun classifiesStreamHttpFailuresWithoutCompletingTheRun() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(statusCode = 404, contentType = "application/json", body = "{\"detail\":\"gone\"}"),
                ScriptedResponse(statusCode = 409, contentType = "application/json", body = "{\"detail\":\"other worker\"}"),
                ScriptedResponse(statusCode = 500, contentType = "application/json", body = "{\"detail\":\"unavailable\"}"),
            ),
        )
        try {
            val api = DeerFlowApi(server.url, NoopSessionCookieStore)
            val statuses = listOf(404, 409, 500).map { _ ->
                val result = api.streamMessage("thread-1", "Conflict", RunOptions()) { }
                assertTrue(result is StreamResult.HttpFailure)
                (result as StreamResult.HttpFailure).statusCode
            }

            assertEquals(listOf(404, 409, 500), statuses)
        } finally {
            server.close()
        }
    }

    @Test
    fun persistsSkillEnabledStateThroughTheGatewayContract() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(
                    contentType = "application/json",
                    body = "{\"name\":\"writing-studio\",\"description\":\"Draft reports\",\"category\":\"public\",\"enabled\":false}",
                ),
            ),
        )
        try {
            val skill = DeerFlowApi(server.url, NoopSessionCookieStore).setSkillEnabled("writing-studio", false)

            assertEquals("writing-studio", skill.name)
            assertFalse(skill.enabled)
            assertEquals("PUT", server.requests.single().method)
            assertEquals("/api/skills/writing-studio", server.requests.single().path)
            assertFalse(JSONObject(server.requests.single().body).getBoolean("enabled"))
        } finally {
            server.close()
        }
    }

    @Test
    fun readsScheduledTaskExecutionHistoryThroughTheGatewayContract() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(
                    contentType = "application/json",
                    body = """[{"id":"task-run-1","task_id":"daily-brief","thread_id":"thread-1","run_id":"gateway-run-1","scheduled_for":"2026-07-20T09:00:00+08:00","trigger":"scheduled","status":"success","error":null,"started_at":"2026-07-20T09:00:04+08:00","finished_at":"2026-07-20T09:01:09+08:00","created_at":"2026-07-20T09:00:00+08:00"}]""",
                ),
            ),
        )
        try {
            val runs = DeerFlowApi(server.url, NoopSessionCookieStore).listScheduledTaskRuns("daily-brief")

            assertEquals(1, runs.size)
            assertEquals("task-run-1", runs.single().id)
            assertEquals("thread-1", runs.single().threadId)
            assertEquals("gateway-run-1", runs.single().runId)
            assertEquals("success", runs.single().status)
            assertNull(runs.single().error)
            assertEquals("GET", server.requests.single().method)
            assertEquals("/api/scheduled-tasks/daily-brief/runs", server.requests.single().path)
        } finally {
            server.close()
        }
    }

    @Test
    fun createsAndUpdatesOnceTasksThroughTheGatewayContract() = runBlocking {
        val schedule = TaskSchedule.Once("2026-12-31T09:30:00+08:00")
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(contentType = "application/json", body = "{}"),
                ScriptedResponse(contentType = "application/json", body = "{}"),
            ),
        )
        try {
            val api = DeerFlowApi(server.url, NoopSessionCookieStore)
            api.createScheduledTask("Release review", "Review the release", schedule, "Asia/Shanghai")
            api.updateScheduledTask("release once", "Updated review", "Review the final release", schedule, "Asia/Shanghai")

            val create = JSONObject(server.requests[0].body)
            assertEquals("POST", server.requests[0].method)
            assertEquals("/api/scheduled-tasks", server.requests[0].path)
            assertEquals("once", create.getString("schedule_type"))
            assertEquals(schedule.runAt, create.getJSONObject("schedule_spec").getString("run_at"))

            val update = JSONObject(server.requests[1].body)
            assertEquals("PATCH", server.requests[1].method)
            assertEquals("/api/scheduled-tasks/release+once", server.requests[1].path)
            assertFalse(update.has("schedule_type"))
            assertEquals(schedule.runAt, update.getJSONObject("schedule_spec").getString("run_at"))
        } finally {
            server.close()
        }
    }

    @Test
    fun readsOneTimeTaskScheduleFromTheGatewayContract() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(
                    contentType = "application/json",
                    body = """[{"id":"release-review","title":"Release review","prompt":"Review the release","schedule_type":"once","schedule_spec":{"run_at":"2026-12-31T09:30:00+08:00"},"timezone":"Asia/Shanghai","status":"enabled","next_run_at":"2026-12-31T09:30:00+08:00","last_error":null,"run_count":0}]""",
                ),
            ),
        )
        try {
            val task = DeerFlowApi(server.url, NoopSessionCookieStore).listScheduledTasks().single()

            assertEquals("once", task.scheduleType)
            assertEquals("2026-12-31T09:30:00+08:00", task.scheduleLabel)
            assertEquals("Asia/Shanghai", task.timezone)
            assertEquals("GET", server.requests.single().method)
            assertEquals("/api/scheduled-tasks", server.requests.single().path)
        } finally {
            server.close()
        }
    }

    @Test
    fun readsAgentExecutionHistoryAndSafelyEncodesTheAgentFilter() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(
                    contentType = "application/json",
                    body =
                        """{"runs":[{"run_id":"run-1","thread_id":"thread-1","thread_title":"Release review","assistant_id":"research & review/+?#%","status":"success","model_name":"deepseek-chat","created_at":"2026-07-20T09:00:00Z","updated_at":"2026-07-20T09:00:12Z","duration_seconds":12.5,"total_tokens":4312,"message_count":8,"cost":0.012345,"error":null},{"run_id":"run-2","thread_id":"thread-2","thread_title":"Other agent","assistant_id":"other-agent","status":"success","model_name":null,"created_at":null,"updated_at":null,"duration_seconds":null,"total_tokens":0,"message_count":0,"cost":null,"error":null},{"run_id":"run-3","thread_id":"thread-3","thread_title":null,"assistant_id":"research & review/+?#%","status":"error","model_name":null,"created_at":null,"updated_at":null,"duration_seconds":null,"total_tokens":0,"message_count":0,"cost":null,"error":"Provider unavailable"}],"has_more":false}""",
                ),
            ),
        )
        try {
            val runs = DeerFlowApi(server.url, NoopSessionCookieStore).listAgentRuns(
                agentId = "research & review/+?#%",
            )

            // Gateways without the assistant_id query filter return every agent's runs;
            // the client must drop the rows that belong to other agents.
            assertEquals(2, runs.size)
            assertEquals(
                AgentRunInfo(
                    runId = "run-1",
                    threadId = "thread-1",
                    threadTitle = "Release review",
                    assistantId = "research & review/+?#%",
                    status = "success",
                    modelName = "deepseek-chat",
                    createdAt = "2026-07-20T09:00:00Z",
                    updatedAt = "2026-07-20T09:00:12Z",
                    durationSeconds = 12.5,
                    totalTokens = 4312,
                    messageCount = 8,
                    cost = 0.012345,
                    error = null,
                ),
                runs.first(),
            )
            assertEquals("run-3", runs.last().runId)
            assertNull(runs.last().threadTitle)
            assertNull(runs.last().modelName)
            assertNull(runs.last().createdAt)
            assertNull(runs.last().updatedAt)
            assertNull(runs.last().durationSeconds)
            assertNull(runs.last().cost)
            assertEquals("Provider unavailable", runs.last().error)
            assertEquals("GET", server.requests.single().method)
            assertEquals(
                "/api/console/runs?assistant_id=research%20%26%20review%2F%2B%3F%23%25&limit=50",
                server.requests.single().path,
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun fillsAgentExecutionHistoryAcrossPagesWhenGatewayIgnoresAssistantFilter() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(
                    contentType = "application/json",
                    body =
                        """{"runs":[{"run_id":"other-1","thread_id":"thread-other-1","assistant_id":"other-agent","status":"success"},{"run_id":"other-2","thread_id":"thread-other-2","assistant_id":"other-agent","status":"success"}],"has_more":true}""",
                ),
                ScriptedResponse(
                    contentType = "application/json",
                    body =
                        """{"runs":[{"run_id":"target-1","thread_id":"thread-target-1","assistant_id":"target-agent","status":"success"},{"run_id":"target-2","thread_id":"thread-target-2","assistant_id":"target-agent","status":"error"}],"has_more":false}""",
                ),
            ),
        )
        try {
            val runs = DeerFlowApi(server.url, NoopSessionCookieStore).listAgentRuns(
                agentId = "target-agent",
                limit = 2,
            )

            assertEquals(listOf("target-1", "target-2"), runs.map { it.runId })
            assertEquals(2, server.requests.size)
            assertEquals(
                "/api/console/runs?assistant_id=target-agent&limit=2",
                server.requests[0].path,
            )
            assertEquals(
                "/api/console/runs?assistant_id=target-agent&limit=2&offset=2",
                server.requests[1].path,
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun listThreadsRequestsTheGivenPageWindow() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(
                    contentType = "application/json",
                    body =
                        """[{"thread_id":"thread-3","status":"idle","updated_at":"2026-07-18T09:00:00Z","values":{"title":"Older chat"}},{"thread_id":"thread-4","status":"idle","updated_at":"2026-07-17T09:00:00Z","metadata":{"title":"Oldest chat"}}]""",
                ),
            ),
        )
        try {
            val threads = DeerFlowApi(server.url, NoopSessionCookieStore).listThreads(limit = 2, offset = 2)

            assertEquals(listOf("thread-3", "thread-4"), threads.map { it.id })
            assertEquals("Older chat", threads.first().title)
            assertEquals("Oldest chat", threads.last().title)
            val request = server.requests.single()
            assertEquals("POST", request.method)
            assertEquals("/api/threads/search", request.path)
            assertEquals("""{"limit":2,"offset":2}""", request.body)
        } finally {
            server.close()
        }
    }

    @Test
    fun readsPublicSsoProvidersAndBuildsAnEncodedGatewayLoginUrl() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(
                    contentType = "application/json",
                    body = """{"providers":[{"id":"google","display_name":"Google","type":"oidc"},{"id":"keycloak","display_name":"Company SSO","type":"oidc"},{"display_name":"Invalid"}]}""",
                ),
            ),
        )
        try {
            val api = DeerFlowApi(server.url, NoopSessionCookieStore)

            assertEquals(listOf("Company SSO", "Google"), api.ssoProviders().map { it.displayName })
            assertEquals("GET", server.requests.single().method)
            assertEquals("/api/v1/auth/providers", server.requests.single().path)
            assertEquals("${server.url}/api/v1/auth/oauth/keycloak", api.ssoLoginUrl("keycloak"))
        } finally {
            server.close()
        }
    }

    @Test
    fun persistsOnlyTheTargetMcpServerToggleWhilePreservingMaskedConfiguration() = runBlocking {
        val initial = """{"mcp_servers":{"research":{"enabled":true,"type":"http","headers":{"Authorization":"***"},"description":"Research","tools":{"search":{"enabled":true}},"future_field":{"mode":"strict"}},"files":{"enabled":false,"type":"stdio","command":"npx","args":["-y","files"]}}}"""
        val updated = """{"mcp_servers":{"research":{"enabled":false,"type":"http","headers":{"Authorization":"***"},"description":"Research","tools":{"search":{"enabled":true}},"future_field":{"mode":"strict"}},"files":{"enabled":false,"type":"stdio","command":"npx","args":["-y","files"]}}}"""
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(contentType = "application/json", body = initial),
                ScriptedResponse(contentType = "application/json", body = updated),
            ),
        )
        try {
            val api = DeerFlowApi(server.url, NoopSessionCookieStore)
            val config = api.loadMcpConfig()
            val result = api.setMcpServerEnabled(config, "research", false)

            assertFalse(result.servers.single { it.name == "research" }.enabled)
            assertEquals(listOf("search"), result.servers.single { it.name == "research" }.toolOverrides)
            assertEquals("GET", server.requests[0].method)
            assertEquals("PUT", server.requests[1].method)
            val request = JSONObject(server.requests[1].body).getJSONObject("mcp_servers")
            assertFalse(request.getJSONObject("research").getBoolean("enabled"))
            assertEquals("***", request.getJSONObject("research").getJSONObject("headers").getString("Authorization"))
            assertEquals("strict", request.getJSONObject("research").getJSONObject("future_field").getString("mode"))
            assertFalse(request.getJSONObject("files").getBoolean("enabled"))
        } finally {
            server.close()
        }
    }

    @Test
    fun readsMcpToolCatalogAndSavesFullMaskedConfiguration() = runBlocking {
        val tools = """{"tools":[{"server_name":"research","name":"summarize","description":"Summarize sources"},{"server_name":"research","name":"search","description":"Search sources"}]}"""
        val updated = """{"mcp_servers":{"research":{"enabled":true,"type":"http","headers":{"Authorization":"***"},"description":"Research","routing":{"keywords":["research"]}}}}"""
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(contentType = "application/json", body = tools),
                ScriptedResponse(contentType = "application/json", body = updated),
            ),
        )
        try {
            val api = DeerFlowApi(server.url, NoopSessionCookieStore)
            val catalog = api.loadMcpTools()
            val config = api.updateMcpConfig(updated)

            assertEquals(listOf("search", "summarize"), catalog.map { it.name })
            assertEquals("research", catalog.first().serverName)
            assertEquals("GET", server.requests[0].method)
            assertEquals("/api/mcp/tools", server.requests[0].path)
            assertEquals("PUT", server.requests[1].method)
            assertEquals("***", JSONObject(server.requests[1].body)
                .getJSONObject("mcp_servers")
                .getJSONObject("research")
                .getJSONObject("headers")
                .getString("Authorization"))
            assertEquals("Research", config.servers.single().description)
        } finally {
            server.close()
        }
    }

    @Test
    fun fallsBackToConfiguredMcpToolsWhenDiscoveryIsUnavailable() = runBlocking {
        val config = """{"mcp_servers":{"research":{"enabled":true,"type":"http","description":"Research sources","tools":["search","summarize"]},"files":{"enabled":true,"type":"stdio","description":"Workspace files"}}}"""
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(statusCode = 404, contentType = "application/json", body = "{\"detail\":\"Not found\"}"),
                ScriptedResponse(contentType = "application/json", body = config),
            ),
        )
        try {
            val catalog = DeerFlowApi(server.url, NoopSessionCookieStore).loadMcpTools()

            assertEquals(
                listOf("files:MCP server", "research:search", "research:summarize"),
                catalog.map { "${it.serverName}:${it.name}" },
            )
            assertEquals(listOf("/api/mcp/tools", "/api/mcp/config"), server.requests.map { it.path })
        } finally {
            server.close()
        }
    }

    @Test
    fun managesChannelRuntimeConfigAndBindingThroughTheGatewayContract() = runBlocking {
        val providers = """{"enabled":true,"providers":[{"provider":"telegram","display_name":"Telegram","enabled":true,"configured":false,"connectable":false,"unavailable_reason":"Runtime credentials are required.","auth_mode":"deep_link","connection_status":"not_connected","credential_fields":[{"name":"bot_token","label":"Bot token","type":"password","required":true},{"name":"bot_username","label":"Bot username","type":"text","required":true}],"credential_values":{}}]}"""
        val configured = """{"provider":"telegram","display_name":"Telegram","enabled":true,"configured":true,"connectable":true,"unavailable_reason":null,"auth_mode":"deep_link","connection_status":"not_connected","credential_fields":[{"name":"bot_token","label":"Bot token","type":"password","required":true},{"name":"bot_username","label":"Bot username","type":"text","required":true}],"credential_values":{"bot_token":"********","bot_username":"fixture_bot"}}"""
        val disconnected = """{"provider":"telegram","display_name":"Telegram","enabled":true,"configured":false,"connectable":false,"unavailable_reason":"Runtime credentials are required.","auth_mode":"deep_link","connection_status":"not_connected","credential_fields":[],"credential_values":{}}"""
        val connection = """{"provider":"telegram","mode":"deep_link","url":"https://channels.example.test/telegram","code":"bind-telegram","instruction":"Open Telegram to finish binding.","expires_in":600}"""
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(contentType = "application/json", body = providers),
                ScriptedResponse(contentType = "application/json", body = configured),
                ScriptedResponse(contentType = "application/json", body = disconnected),
                ScriptedResponse(contentType = "application/json", body = connection),
            ),
        )
        try {
            val api = DeerFlowApi(server.url, NoopSessionCookieStore)
            val initial = api.loadChannelProviders()
            val updated = api.configureChannelProvider(
                "telegram",
                mapOf("bot_token" to "secret", "bot_username" to "fixture_bot"),
            )
            val disabled = api.disconnectChannelProvider("telegram")
            val binding = api.connectChannelProvider("telegram")

            assertFalse(initial.providers.single().configured)
            assertEquals("password", initial.providers.single().credentialFields.first().type)
            assertTrue(updated.configured)
            assertEquals("********", updated.credentialValues["bot_token"])
            assertNull(updated.unavailableReason)
            assertFalse(disabled.configured)
            assertEquals("bind-telegram", binding.code)
            assertEquals("https://channels.example.test/telegram", binding.url)

            assertEquals("GET", server.requests[0].method)
            assertEquals("/api/channels/providers", server.requests[0].path)
            assertEquals("POST", server.requests[1].method)
            assertEquals("/api/channels/telegram/runtime-config", server.requests[1].path)
            val configuration = JSONObject(server.requests[1].body).getJSONObject("values")
            assertEquals("secret", configuration.getString("bot_token"))
            assertEquals("fixture_bot", configuration.getString("bot_username"))
            assertEquals("DELETE", server.requests[2].method)
            assertEquals("/api/channels/telegram/runtime-config", server.requests[2].path)
            assertEquals("POST", server.requests[3].method)
            assertEquals("/api/channels/telegram/connect", server.requests[3].path)
        } finally {
            server.close()
        }
    }

    @Test
    fun exposesSseErrorAsFailureUpdateBeforeEnd() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                sse(
                    "event: error\nid: error-1\ndata: {\"message\":\"Provider unavailable\"}",
                    "event: end\nid: end-1\ndata: null",
                ),
            ),
        )
        try {
            val updates = mutableListOf<StreamUpdate>()
            DeerFlowApi(server.url, NoopSessionCookieStore).streamMessage(
                threadId = "thread-1",
                message = "Surface the error",
                options = RunOptions(),
            ) { updates += it }

            assertEquals(
                listOf("Provider unavailable"),
                updates.filterIsInstance<StreamUpdate.Failure>().map { it.message },
            )
            assertTrue(updates.last() == StreamUpdate.Finished)
        } finally {
            server.close()
        }
    }

    @Test
    fun structuredHumanInputStopsTheLocalStreamWithoutWaitingForSseEnd() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                sse(
                    "event: metadata\nid: metadata-1\ndata: {\"run_id\":\"run-1\"}",
                    """event: messages-tuple
id: input-1
data: {"type":"tool","id":"clarification-1","tool_call_id":"call-1","name":"ask_clarification","content":"Which environment should I use?","artifact":{"human_input":{"version":1,"kind":"human_input_request","source":"ask_clarification","request_id":"clarification-1","tool_call_id":"call-1","question":"Which environment should I use?","input_mode":"free_text"}}}""",
                ),
            ),
        )
        try {
            var awaitingHumanInput = false
            val updates = mutableListOf<StreamUpdate>()
            val result = DeerFlowApi(server.url, NoopSessionCookieStore).streamMessage(
                threadId = "thread-1",
                message = "Deploy the service",
                options = RunOptions(),
                shouldStop = { awaitingHumanInput },
            ) { update ->
                updates += update
                awaitingHumanInput = updates
                    .filterIsInstance<StreamUpdate.MessageChunk>()
                    .any { chunk -> hasOpenHumanInputRequest(listOf(chunk.value)) }
            }

            assertEquals(StreamResult.AwaitingHumanInput("run-1", "input-1"), result)
            assertFalse(updates.any { it == StreamUpdate.Finished })
        } finally {
            server.close()
        }
    }

    @Test
    fun decodesCustomSubagentLifecycleEvents() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                sse(
                    "event: custom\nid: task-start\ndata: {\"type\":\"task_started\",\"task_id\":\"task-1\",\"description\":\"Inspect the API\",\"model_name\":\"deerflow-pro\"}",
                    "event: custom\nid: task-ai\ndata: {\"type\":\"task_running\",\"task_id\":\"task-1\",\"message_index\":1,\"model_name\":\"deerflow-pro\",\"message\":{\"type\":\"ai\",\"content\":[\"Read docs\",{\"text\":\"then compare contracts\"}],\"tool_calls\":[{\"name\":\"web_search\"}]}}",
                    "event: custom\nid: task-tool\ndata: {\"type\":\"task_running\",\"task_id\":\"task-1\",\"message_index\":2,\"message\":{\"type\":\"tool\",\"name\":\"web_search\",\"content\":\"Found docs\"}}",
                    "event: custom\nid: task-complete\ndata: {\"type\":\"task_completed\",\"task_id\":\"task-1\",\"result\":\"Contract verified\",\"model_name\":\"deerflow-pro\"}",
                    "event: custom\nid: task-cancelled\ndata: {\"type\":\"task_cancelled\",\"task_id\":\"task-2\",\"error\":\"Stopped by user\"}",
                    "event: end\nid: end-1\ndata: null",
                ),
            ),
        )
        try {
            val updates = mutableListOf<StreamUpdate>()
            DeerFlowApi(server.url, NoopSessionCookieStore).streamMessage(
                threadId = "thread-1",
                message = "Track a subagent",
                options = RunOptions(),
            ) { updates += it }

            val progress = updates.filterIsInstance<StreamUpdate.SubagentProgress>()
            assertEquals(5, progress.size)
            assertEquals("Inspect the API", progress[0].description)
            assertEquals("deerflow-pro", progress[0].modelName)
            assertEquals(
                MessageBlock.SubtaskStep(
                    messageIndex = 1,
                    kind = "ai",
                    text = "Read docs\nthen compare contracts",
                    toolCalls = listOf("web_search"),
                ),
                progress[1].step,
            )
            assertEquals(
                MessageBlock.SubtaskStep(
                    messageIndex = 2,
                    kind = "tool",
                    text = "Found docs",
                    toolName = "web_search",
                ),
                progress[2].step,
            )
            assertEquals(MessageBlock.SubtaskStatus.Completed, progress[3].status)
            assertEquals("Contract verified", progress[3].result)
            assertEquals(MessageBlock.SubtaskStatus.Failed, progress[4].status)
            assertEquals("Stopped by user", progress[4].error)
        } finally {
            server.close()
        }
    }

    @Test
    fun decodesGatewayRunNoticesWithoutTaskIdsAndIgnoresNamespacedFrames() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                sse(
                    """event: messages-tuple|lead_agent:subtask
id: namespaced-message
data: {"type":"ai","id":"ignored","content":"Do not merge this"}""",
                    """event: updates|lead_agent:subtask
id: namespaced-update
data: {"agent":{"title":"Do not use this title"}}""",
                    """event: custom
id: retry-1
data: {"type":"llm_retry","attempt":2,"max_attempts":3,"wait_ms":250,"reason":"rate_limit","message":"Retrying after a rate limit."}""",
                    """event: custom
id: safety-1
data: {"type":"safety_termination","reason_field":"finish_reason","reason_value":"content_filter"}""",
                    """event: messages-tuple
id: root-message
data: {"type":"ai","id":"root","content":"Visible answer"}""",
                    """event: end
id: end-1
data: null""",
                ),
            ),
        )
        try {
            val updates = mutableListOf<StreamUpdate>()
            DeerFlowApi(server.url, NoopSessionCookieStore).streamMessage(
                threadId = "thread-1",
                message = "Show notices",
                options = RunOptions(),
            ) { updates += it }

            assertEquals(
                listOf("Visible answer"),
                updates.filterIsInstance<StreamUpdate.MessageChunk>().map { it.value.text },
            )
            val notices = updates.filterIsInstance<StreamUpdate.RunNotice>()
            assertEquals(2, notices.size)
            assertEquals(RunNoticeKind.LlmRetry, notices[0].kind)
            assertEquals(2, notices[0].attempt)
            assertEquals(3, notices[0].maxAttempts)
            assertEquals(250L, notices[0].waitMillis)
            assertEquals(RunNoticeKind.SafetyTermination, notices[1].kind)
            assertEquals("content_filter", notices[1].message)
        } finally {
            server.close()
        }
    }

    @Test
    fun streamRequestUsesOnlyTheSelectedModelsSupportedOptions() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                sse("event: end\nid: fast-end\ndata: null"),
                sse("event: end\nid: pro-end\ndata: null"),
            ),
        )
        try {
            val api = DeerFlowApi(server.url, NoopSessionCookieStore)
            api.streamMessage(
                threadId = "thread-fast",
                message = "Use the fast model",
                options = RunOptions(
                    modelName = "deerflow-fast",
                    mode = RunMode.Flash,
                    reasoningEffortEnabled = false,
                ),
            ) { }
            api.streamMessage(
                threadId = "thread-pro",
                message = "Use the pro model",
                options = RunOptions(
                    modelName = "deerflow-pro",
                    mode = RunMode.Ultra,
                    reasoningEffortEnabled = true,
                ),
            ) { }

            val fastContext = JSONObject(server.requests[0].body).getJSONObject("context")
            assertEquals("deerflow-fast", fastContext.getString("model_name"))
            assertFalse(fastContext.getBoolean("thinking_enabled"))
            assertFalse(fastContext.getBoolean("is_plan_mode"))
            assertFalse(fastContext.getBoolean("subagent_enabled"))
            assertFalse(fastContext.has("reasoning_effort"))

            val proContext = JSONObject(server.requests[1].body).getJSONObject("context")
            assertEquals("deerflow-pro", proContext.getString("model_name"))
            assertTrue(proContext.getBoolean("thinking_enabled"))
            assertTrue(proContext.getBoolean("is_plan_mode"))
            assertTrue(proContext.getBoolean("subagent_enabled"))
            assertEquals("high", proContext.getString("reasoning_effort"))
        } finally {
            server.close()
        }
    }

    @Test
    fun sendsTheStableClientMessageIdAndDecodesEveryOrderedUpdatePatchWithoutValuesStream() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                sse(
                    "event: updates\nid: update-1\ndata: {\"agent\":{\"title\":\"Research\",\"todos\":[{\"content\":\"Write\",\"status\":\"in_progress\"}],\"artifacts\":[\"report.md\"]},\"tools\":{\"messages\":[{\"type\":\"tool\",\"id\":\"tool-1\",\"tool_call_id\":\"call-1\",\"name\":\"search\",\"content\":\"done\"}],\"artifacts\":[\"report.md\",\"chart.png\"]},\"final\":{\"title\":\"Final research\",\"todos\":[],\"messages\":[{\"type\":\"remove\",\"id\":\"stale-ai\"}]}}",
                    "event: end\nid: end-1\ndata: null",
                ),
            ),
        )
        try {
            val updates = mutableListOf<StreamUpdate>()
            DeerFlowApi(server.url, NoopSessionCookieStore).streamMessage(
                threadId = "thread-1",
                message = "Research this",
                options = RunOptions(),
                clientMessageId = "client-message-1",
            ) { updates += it }

            val request = server.requests.single()
            val payload = JSONObject(request.body)
            assertEquals("client-message-1", payload.getJSONObject("input").getJSONArray("messages").getJSONObject(0).getString("id"))
            assertEquals(listOf("messages-tuple", "updates", "custom"), payload.getJSONArray("stream_mode").let { modes ->
                List(modes.length()) { modes.getString(it) }
            })
            assertFalse(payload.getBoolean("stream_subgraphs"))
            assertFalse(payload.getBoolean("stream_resumable"))
            assertEquals("continue", payload.getString("on_disconnect"))
            assertFalse(request.headers["accept-encoding"].equals("identity", ignoreCase = true))

            val patches = updates.filterIsInstance<StreamUpdate.Patch>().map { it.value }
            assertEquals(3, patches.size)
            assertEquals("Research", patches[0].title)
            assertEquals("Write", patches[0].todos?.single()?.content)
            assertEquals(listOf("report.md"), patches[0].artifacts)
            assertTrue(patches[1].messages.single() is StreamMessageOperation.Upsert)
            assertEquals(listOf("report.md", "chart.png"), patches[1].artifacts)
            assertEquals("Final research", patches[2].title)
            assertEquals(emptyList<TodoItem>(), patches[2].todos)
            assertEquals(StreamMessageOperation.Remove("stale-ai"), patches[2].messages.single())
        } finally {
            server.close()
        }
    }

    @Test
    fun polishesInputWithLocaleAndOptionalThreadContext() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(
                    contentType = "application/json",
                    body = """{"rewritten_text":"Please produce a concise release plan.","changed":true}""",
                ),
                ScriptedResponse(
                    contentType = "application/json",
                    body = """{"rewritten_text":"Already clear","changed":false}""",
                ),
            ),
        )
        try {
            val result = DeerFlowApi(server.url, NoopSessionCookieStore).polishInput(
                text = "make release plan",
                locale = "en-US",
                threadId = "thread-1",
            )

            assertEquals("Please produce a concise release plan.", result.rewrittenText)
            assertTrue(result.changed)
            val request = server.requests.first()
            assertEquals("POST", request.method)
            assertEquals("/api/input-polish", request.path)
            val body = JSONObject(request.body)
            assertEquals("make release plan", body.getString("text"))
            assertEquals("en-US", body.getString("locale"))
            assertEquals("thread-1", body.getString("thread_id"))

            DeerFlowApi(server.url, NoopSessionCookieStore).polishInput(
                text = "Already clear",
                locale = null,
                threadId = null,
            )
            val contextFreeBody = JSONObject(server.requests.last().body)
            assertFalse(contextFreeBody.has("locale"))
            assertFalse(contextFreeBody.has("thread_id"))
        } finally {
            server.close()
        }
    }

    @Test
    fun unexpectedEofRecoversAtMostOnce() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                sse("event: metadata\nid: one\ndata: {\"run_id\":\"run-1\"}"),
                sse("event: messages-tuple\nid: two\ndata: {\"type\":\"ai\",\"id\":\"ai-1\",\"content\":\"partial\"}"),
                sse("event: end\nid: unexpected\ndata: null"),
            ),
        )
        try {
            DeerFlowApi(server.url, NoopSessionCookieStore).streamMessage(
                threadId = "thread-1",
                message = "Recover once",
                options = RunOptions(),
            ) { }

            assertEquals(2, server.requests.size)
            assertEquals("run-1", server.requests[1].path.substringAfter("/runs/").substringBefore('/'))
        } finally {
            server.close()
        }
    }

    @Test
    fun exhaustedEofRecoveryReturnsRetryableDisconnectWithoutFinished() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                sse("event: metadata\nid: event-1\ndata: {\"run_id\":\"run-1\"}"),
                ScriptedResponse(),
            ),
        )
        try {
            val updates = mutableListOf<StreamUpdate>()
            val result = DeerFlowApi(
                server.url,
                NoopSessionCookieStore,
                StreamReconnectPolicy(maxUnexpectedEofReconnects = 1, reconnectDelayMs = 0),
            ).streamMessage(
                threadId = "thread-1",
                message = "Keep recovering",
                options = RunOptions(),
            ) { updates += it }

            assertEquals(StreamResult.RetryableDisconnect("run-1", "event-1", 1), result)
            assertEquals(2, server.requests.size)
            assertEquals("event-1", server.requests[1].headers["last-event-id"])
            assertFalse(updates.any { it == StreamUpdate.Finished })
        } finally {
            server.close()
        }
    }

    @Test
    fun resumeByteSliceKeepsTheLastEventIdForTheNextJoin() = runBlocking {
        val largeChunk = "x".repeat(270 * 1024)
        val server = ScriptedSseServer(
            listOf(
                sse("event: metadata\nid: initial\ndata: {\"run_id\":\"run-1\"}"),
                sse(
                    "event: messages-tuple\nid: event-2\ndata: {\"type\":\"ai\",\"id\":\"ai-1\",\"content\":\"$largeChunk\"}",
                ),
                sse("event: end\nid: event-3\ndata: null"),
            ),
        )
        try {
            val result = withTimeout(10_000) {
                DeerFlowApi(
                    server.url,
                    NoopSessionCookieStore,
                    StreamReconnectPolicy(reconnectDelayMs = 0, maxResumeBytes = 256L * 1024L),
                ).streamMessage("thread-1", "Slice the replay", RunOptions()) { }
            }

            assertTrue(result is StreamResult.TerminalEnd)
            assertEquals(3, server.requests.size)
            // The byte limit can land in the middle of event-2. Resume from the last fully
            // decoded event so the complete frame is replayed and processed exactly once.
            assertEquals("initial", server.requests[2].headers["last-event-id"])
        } finally {
            server.close()
        }
    }

    @Test
    fun resumeTimeSliceStopsTheCurrentConnectionBeforeReadingAnotherFrame() {
        val result = DeerFlowApi("http://127.0.0.1", NoopSessionCookieStore).readEventStream(
            input = ByteArrayInputStream("event: end\ndata: null\n\n".toByteArray(StandardCharsets.UTF_8)),
            maxBytes = null,
            maxDurationMs = -1,
            onEvent = { true },
        )

        assertEquals(StreamEndReason.ResumeTimeLimit, result.reason)
    }

    @Test
    fun getRunParsesTerminalStatusAndStopReason() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(
                    contentType = "application/json",
                    body = """{"run_id":"run-1","thread_id":"thread-1","status":"interrupted","stop_reason":"Stopped by user"}""",
                ),
            ),
        )
        try {
            val run = DeerFlowApi(server.url, NoopSessionCookieStore).getRun("thread-1", "run-1")

            assertEquals("run-1", run.runId)
            assertEquals(GatewayRunStatus.Interrupted, run.status)
            assertEquals("Stopped by user", run.stopReason)
            assertEquals("GET", server.requests.single().method)
            assertEquals("/api/threads/thread-1/runs/run-1", server.requests.single().path)
        } finally {
            server.close()
        }
    }

    @Test
    fun readsRunAuditAndWorkspaceChangesThroughGatewayContracts() = runBlocking {
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(
                    contentType = "application/json",
                    body = """[{"run_id":"run-1","thread_id":"thread-1","assistant_id":"lead_agent","status":"success","created_at":"2026-07-26T10:00:00Z","updated_at":"2026-07-26T10:01:00Z","total_input_tokens":12,"total_output_tokens":34,"total_tokens":46,"llm_call_count":2,"lead_agent_tokens":20,"subagent_tokens":20,"middleware_tokens":6,"message_count":3,"stop_reason":null}]""",
                ),
                ScriptedResponse(
                    contentType = "application/json",
                    body = """[{"seq":7,"event_type":"subagent.step","category":"subagent","content":{"tool":"web_search"},"metadata":{"task_id":"task-1"},"created_at":"2026-07-26T10:00:30Z"}]""",
                ),
                ScriptedResponse(
                    contentType = "application/json",
                    body = """{"available":true,"version":1,"summary":{"created":1,"modified":2,"deleted":0,"symlink_created":0,"additions":12,"deletions":3,"truncated":false},"files":[{"path":"outputs/report.md","root":"workspace","status":"modified","binary":false,"sensitive":false,"size_before":10,"size_after":19,"diff":"+report","diff_truncated":false,"additions":12,"deletions":3}]}""",
                ),
            ),
        )
        try {
            val api = DeerFlowApi(server.url, NoopSessionCookieStore)
            val details = api.listRunDetails("thread-1")
            val events = api.listRunEvents("thread-1", "run-1")
            val changes = api.workspaceChanges("thread-1", "run-1")

            assertEquals(46, details.single().totalTokens)
            assertEquals(GatewayRunStatus.Success, details.single().status)
            assertEquals("task-1", events.single().taskId)
            assertTrue(events.single().content.contains("web_search"))
            assertTrue(changes.available)
            assertEquals(2, changes.summary.modified)
            assertEquals("outputs/report.md", changes.files.single().path)
            assertEquals(12, changes.files.single().additions)
            assertEquals("/api/threads/thread-1/runs", server.requests[0].path)
            assertEquals("/api/threads/thread-1/runs/run-1/events?limit=500", server.requests[1].path)
            assertEquals(
                "/api/threads/thread-1/runs/run-1/workspace-changes?include_files=true&include_diff=true",
                server.requests[2].path,
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun drivesTheLarkDeviceCodeGatewayContracts() = runBlocking {
        val status = """{"installed":true,"version":"1.2.3","latest_available_version":"1.2.4","runtime_version_mismatch":false,"app_configured":true,"app_id":"cli_123","app_brand":"feishu","skills_expected":4,"skills_installed":4,"enabled_skills":["lark-docs"],"cli":{"available":true,"version":"1.2.3","error":null},"auth":{"status":"authenticated","message":null,"user":"Ada","verified":true},"sandbox_runtime_ready":true,"sandbox_runtime_detail":null}"""
        val server = ScriptedSseServer(
            listOf(
                ScriptedResponse(contentType = "application/json", body = status),
                ScriptedResponse(contentType = "application/json", body = """{"verification_url":"https://lark.example.test/config","device_code":"config-device","expires_in":300,"interval":2,"user_code":"ABCD","brand":"lark"}"""),
                ScriptedResponse(contentType = "application/json", body = """{"success":true,"message":"Configured","status":$status}"""),
                ScriptedResponse(contentType = "application/json", body = """{"verification_url":"https://lark.example.test/auth","device_code":"auth-device","expires_in":300,"user_code":"WXYZ","hint":"Approve access"}"""),
                ScriptedResponse(contentType = "application/json", body = """{"success":true,"message":"Authorized","status":$status}"""),
            ),
        )
        try {
            val api = DeerFlowApi(server.url, NoopSessionCookieStore)
            val initial = api.loadLarkIntegrationStatus()
            val configuration = api.startLarkConfiguration("lark")
            val configured = api.completeLarkConfiguration(configuration)
            val authorization = api.startLarkAuthorization()
            val authorized = api.completeLarkAuthorization(authorization)

            assertTrue(initial.auth.authenticated)
            assertEquals(LarkVerificationKind.Configuration, configuration.kind)
            assertEquals("lark", configuration.brand)
            assertTrue(configured.success)
            assertEquals(LarkVerificationKind.Authorization, authorization.kind)
            assertEquals("Approve access", authorization.hint)
            assertTrue(authorized.success)
            assertEquals("GET", server.requests[0].method)
            assertEquals("/api/integrations/lark/status", server.requests[0].path)
            assertEquals("/api/integrations/lark/config/start", server.requests[1].path)
            assertEquals("lark", JSONObject(server.requests[1].body).getString("brand"))
            assertEquals("/api/integrations/lark/config/complete", server.requests[2].path)
            assertEquals("config-device", JSONObject(server.requests[2].body).getString("device_code"))
            assertEquals("/api/integrations/lark/auth/start", server.requests[3].path)
            assertTrue(JSONObject(server.requests[3].body).getBoolean("recommend"))
            assertEquals("/api/integrations/lark/auth/complete", server.requests[4].path)
            assertEquals("auth-device", JSONObject(server.requests[4].body).getString("device_code"))
        } finally {
            server.close()
        }
    }
}

private data class ScriptedResponse(
    val statusCode: Int = 200,
    val contentType: String = "text/event-stream",
    val body: String = "",
    val closeWithoutResponse: Boolean = false,
)

private data class CapturedRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String,
)

private class ScriptedSseServer(
    private val responses: List<ScriptedResponse>,
) : Closeable {
    private val server = ServerSocket(0)
    private val responseIndex = AtomicInteger()
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    val requests = Collections.synchronizedList(mutableListOf<CapturedRequest>())
    val url: String = "http://127.0.0.1:${server.localPort}"

    init {
        executor.execute {
            try {
                while (!server.isClosed) {
                    val client = server.accept()
                    executor.execute { serve(client) }
                }
            } catch (_: Exception) {
                if (!server.isClosed) throw AssertionError("SSE fixture accept loop stopped")
            }
        }
    }

    override fun close() {
        server.close()
        executor.shutdownNow()
    }

    private fun serve(client: Socket) {
        client.use { socket ->
            val reader = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
            val requestLine = reader.readLine() ?: return
            val headers = buildMap {
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val separator = line.indexOf(':')
                    if (separator > 0) put(line.substring(0, separator).lowercase(), line.substring(separator + 1).trim())
                }
            }
            val bodyLength = headers["content-length"]?.toIntOrNull() ?: 0
            val bodyCharacters = CharArray(bodyLength)
            var bodyOffset = 0
            while (bodyOffset < bodyLength) {
                val read = reader.read(bodyCharacters, bodyOffset, bodyLength - bodyOffset)
                if (read < 0) break
                bodyOffset += read
            }
            val separator = requestLine.indexOf(' ')
            val pathSeparator = requestLine.indexOf(' ', separator + 1)
            requests += CapturedRequest(
                method = requestLine.substring(0, separator),
                path = requestLine.substring(separator + 1, pathSeparator),
                headers = headers,
                body = String(bodyCharacters, 0, bodyOffset),
            )

            val scripted = responses.getOrNull(responseIndex.getAndIncrement()) ?: ScriptedResponse(body = "")
            if (scripted.closeWithoutResponse) return
            val body = scripted.body.toByteArray(StandardCharsets.UTF_8)
            val reason = when (scripted.statusCode) {
                409 -> "Conflict"
                500 -> "Internal Server Error"
                else -> "OK"
            }
            val response = buildString {
                append("HTTP/1.1 ${scripted.statusCode} $reason\r\n")
                append("Content-Type: ${scripted.contentType}\r\n")
                append("Content-Length: ${body.size}\r\n")
                append("Content-Location: /api/threads/thread-1/runs/run-1\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(StandardCharsets.UTF_8)
            socket.getOutputStream().apply {
                write(response)
                write(body)
                flush()
            }
        }
    }
}

private fun sse(vararg frames: String): ScriptedResponse = ScriptedResponse(
    body = frames.joinToString("\n\n", postfix = "\n\n"),
)

private object NoopSessionCookieStore : SessionCookieStore {
    override fun cookieHeader(url: String): String? = null

    override fun csrfToken(url: String): String? = null

    override fun capture(url: String, responseHeaders: Map<String?, List<String>>) = Unit

    override fun clear() = Unit
}
