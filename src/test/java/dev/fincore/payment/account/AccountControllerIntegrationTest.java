package dev.fincore.payment.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.payment.account.api.dto.request.CreateAccountRequest;
import dev.fincore.payment.account.api.dto.response.AccountResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
public class AccountControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateAccount() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest("1234567890");

        mockMvc.perform(post("/api/v1/accounts")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").exists())
               .andExpect(jsonPath("$.number").value("1234567890"));
    }

    @Test
    void shouldGetAccountById() throws Exception {
        // 1. Создаём аккаунт
        CreateAccountRequest request = new CreateAccountRequest("9876543210");

        MvcResult createResult = mockMvc.perform(post("/api/v1/accounts")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isCreated())
                                        .andReturn();

        AccountResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                AccountResponse.class
        );
        UUID accountId = created.getId();

        // 2. Получаем его по ID
        mockMvc.perform(get("/api/v1/accounts/{id}", accountId))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value(accountId.toString()))
               .andExpect(jsonPath("$.number").value("9876543210"));
    }

    @Test
    void shouldFailWhenDuplicateNumber() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest("9876543211");

        // Первый раз — успешно
        mockMvc.perform(post("/api/v1/accounts")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isCreated());

        // Второй раз — ошибка (ожидаем 400)
        mockMvc.perform(post("/api/v1/accounts")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404WhenAccountNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/accounts/{id}", nonExistentId))
               .andExpect(status().isNotFound()); // если ваш глобальный обработчик мапит EntityNotFoundException на 404
    }

    @Test
    void shouldFailValidationWhenNumberIsBlank() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest("");

        mockMvc.perform(post("/api/v1/accounts")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.number").exists()); // поле с ошибкой
    }
}
