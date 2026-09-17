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

/**
 * Тест поднимает НАСТОЯЩИЙ Tomcat (RANDOM_PORT), а не MockMvc, и это принципиально.
 * <p>
 * Проверяемый здесь класс ошибок возникает так: MVC отвечает статусом без тела,
 * контейнер запускает ERROR-диспетчеризацию на /error, та повторно проходит через
 * фильтры Spring Security, путь /error под permitAll не подпадает — и исходный
 * статус подменяется на 401 с пустым телом. MockMvc ERROR-диспетчеризацию не
 * имитирует, поэтому в нём такой баг невидим.
 */
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

    /** Почта уникальная на каждый вызов: тесты делят одну базу и порядок их не определён. */
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

    /**
     * Короткий пароль — ошибка клиента, а не отсутствие авторизации.
     * До появления обработчика MethodArgumentNotValidException здесь приходил 401:
     * ответ уходил без тела, срабатывала ERROR-диспетчеризация, и Security
     * отказывала уже по пути /error.
     */
    @Test
    void returnsBadRequest_whenPasswordIsTooShort() throws Exception {
        HttpResponse<String> response = post("/api/auth/register",
                """
                {"email": "%s", "password": "short"}
                """.formatted(uniqueEmail()));

        assertEquals(400, response.statusCode(),
                "ошибка валидации не должна превращаться в 401: " + response.body());
    }

    @Test
    void returnsBadRequest_whenEmailIsMalformed() throws Exception {
        HttpResponse<String> response = post("/api/auth/register",
                """
                {"email": "not-an-email", "password": "password123"}
                """);

        assertEquals(400, response.statusCode());
    }

    /**
     * Отвергнутый пароль не должен возвращаться клиенту. Он почти наверняка похож на
     * настоящий пароль пользователя (человек ошибся на символ), а тело ответа оседает
     * в логах прокси и в консоли браузера. Тест не зависит от формата тела —
     * он требует лишь, чтобы введённого значения там не было.
     */
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

    /**
     * То же тело ответа не должно раскрывать внутреннее устройство приложения:
     * имена пакетов и сигнатуры методов — бесплатная разведка для атакующего.
     */
    @Test
    void doesNotLeakInternals_inValidationError() throws Exception {
        HttpResponse<String> response = post("/api/auth/register",
                """
                {"email": "%s", "password": "short"}
                """.formatted(uniqueEmail()));

        assertFalse(response.body().contains("ru.voropaev"),
                "в тело ответа попали внутренние имена классов: " + response.body());
    }

    /**
     * Регрессия на "/error" в permitAll.
     * <p>
     * Путь /api/auth/register открыт, но HttpMessageNotReadableException не покрыт
     * ни одним @ExceptionHandler. MVC отвечает 400 без тела, контейнер уходит в
     * ERROR-диспетчеризацию на /error, и та повторно проходит через фильтры Security.
     * Пока /error не был разрешён, статус подменялся на 401 — клиент видел отказ
     * в авторизации вместо "ты прислал не JSON" и искал проблему в токене.
     * <p>
     * Проверять это на несуществующем пути нельзя: такой путь сам не входит в permitAll
     * и получает честный 401 на первом же проходе, не доходя до /error.
     */
    @Test
    void returnsBadRequest_whenBodyIsNotJson() throws Exception {
        HttpResponse<String> response = post("/api/auth/register", "это не json");

        assertEquals(400, response.statusCode(),
                "ошибка разбора тела не должна выглядеть как отказ в авторизации");
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
