package ru.voropaev.event_driven_marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AuthControllerIntegrationTest {
    @Value("${local.server.port}")
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private HttpResponse<String> post(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.dev";
    }

    @Test
    void registersUser_andReturnsItsId() throws Exception {
        HttpResponse<String> response = post("/api/auth/register",
                """
                {"email": "%s", "password": "password123"}
                """.formatted(uniqueEmail()));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"id\""), "ожидался UserResponse с id, пришло: " + response.body());
    }

    @Test
    void returnsBadRequestWithoutInternals_whenPasswordIsTooShort() throws Exception {
        HttpResponse<String> response = post("/api/auth/register",
                """
                {"email": "%s", "password": "short"}
                """.formatted(uniqueEmail()));

        assertEquals(400, response.statusCode(),
                "ошибка валидации не должна превращаться в 401: " + response.body());
        assertFalse(response.body().contains("ru.voropaev"), response.body());
    }

    @Test
    void doesNotEchoRejectedPassword_inValidationError() throws Exception {
        String submittedPassword = "sekret7";

        HttpResponse<String> response = post("/api/auth/register",
                """
                {"email": "%s", "password": "%s"}
                """.formatted(uniqueEmail(), submittedPassword));

        assertEquals(400, response.statusCode());
        assertFalse(response.body().contains(submittedPassword),
                "введённый пароль попал в тело ответа: " + response.body());
    }

    @Test
    void returnsConstantMessage_whenBodyIsNotJson() throws Exception {
        String submittedPassword = "sekret-inside-broken-json";

        HttpResponse<String> response = post("/api/auth/register",
                """
                {"email": "%s", "password": "%s",}
                """.formatted(uniqueEmail(), submittedPassword));

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Malformed request body"),
                "ожидалась константа вместо сообщения парсера, пришло: " + response.body());
        assertFalse(response.body().contains(submittedPassword),
                "присланный пароль попал в тело ответа: " + response.body());
        assertFalse(response.body().contains("JSON parse error"),
                "наружу утекло сообщение Jackson: " + response.body());
    }

    @Test
    void returnsConflict_whenEmailAlreadyRegistered() throws Exception {
        String email = uniqueEmail();
        String body = """
                {"email": "%s", "password": "password123"}
                """.formatted(email);

        assertEquals(200, post("/api/auth/register", body).statusCode());

        HttpResponse<String> duplicate = post("/api/auth/register", body);

        assertEquals(409, duplicate.statusCode());
    }

    @Test
    void returnsUnauthorized_whenPasswordIsWrong() throws Exception {
        String email = uniqueEmail();
        post("/api/auth/register", """
                {"email": "%s", "password": "password123"}
                """.formatted(email));

        HttpResponse<String> response = post("/api/auth/login", """
                {"email": "%s", "password": "wrong-password"}
                """.formatted(email));

        assertEquals(401, response.statusCode());
    }

    @Test
    void issuesJwt_onSuccessfulLogin() throws Exception {
        String email = uniqueEmail();
        post("/api/auth/register", """
                {"email": "%s", "password": "password123"}
                """.formatted(email));

        HttpResponse<String> response = post("/api/auth/login", """
                {"email": "%s", "password": "password123"}
                """.formatted(email));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"token\":\"eyJ"),
                "ожидался JWT в поле token, пришло: " + response.body());
    }
}
