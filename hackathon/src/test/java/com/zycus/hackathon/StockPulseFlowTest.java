package com.zycus.hackathon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole review loop through the HTTP API against H2, with the rules strategy active so answers
 * are deterministic: create -> sell -> suggestion -> accept -> on order -> receive.
 */
@SpringBootTest(properties = {
        "stockpulse.advisor.strategy=RULES",
        "stockpulse.demo-data.enabled=false",
        "stockpulse.monitoring.enabled=false"
})
@AutoConfigureMockMvc
class StockPulseFlowTest {

    @Autowired
    private MockMvc mvc;

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void lowStockSaleLeadsToAReorderThatAHumanAcceptsAndReceives() throws Exception {
        long productId = createProduct("FLOW-1", 30);

        // Selling 25 of 30 leaves 5 <= threshold 10.
        JsonNode order = body(post("/api/sales/order", """
                {"items":[{"productId":%d,"quantity":25}]}""".formatted(productId)).andExpect(status().isOk()));
        assertThat(order.at("/items/0/status").asString()).isEqualTo("SUCCESS");
        assertThat(order.at("/items/0/remainingStock").asLong()).isEqualTo(5);

        // The order's after-commit event evaluates the product in the background.
        JsonNode reorder = awaitPendingReorder(productId);
        assertThat(reorder.get("source").asString()).isEqualTo("RULES");
        assertThat(reorder.get("alertType").asString()).isEqualTo("LOW_STOCK");
        long quantity = reorder.get("quantity").asLong();
        assertThat(5 + quantity).isGreaterThan(10);

        // A human accepts it: the quantity is now on order and the alert stops firing.
        long suggestionId = reorder.get("id").asLong();
        post("/api/suggestions/" + suggestionId + "/accept", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        mvc.perform(get("/api/products/" + productId))
                .andExpect(jsonPath("$.onOrderStock").value(quantity));
        post("/api/suggestions/evaluate/" + productId, "")
                .andExpect(jsonPath("$.outcome").value("NO_ALERT"));

        // A second review of the same suggestion loses.
        post("/api/suggestions/" + suggestionId + "/reject", "{\"note\":\"late\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Suggestion " + suggestionId + " is already ACCEPTED"));

        // The delivery arrives.
        post("/api/products/" + productId + "/receive", "{\"quantity\":%d}".formatted(quantity))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStock").value(5 + quantity))
                .andExpect(jsonPath("$.onOrderStock").value(0));
    }

    @Test
    void invalidProductIsRejectedWithFieldErrors() throws Exception {
        post("/api/products", "{\"name\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors[?(@.field == 'sku')]").exists());
    }

    @Test
    void duplicateSkuIsAConflict() throws Exception {
        createProduct("DUP-1", 50);
        post("/api/products", productJson("DUP-1", 50))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("SKU already exists: DUP-1"));
    }

    @Test
    void advisorStrategyCanBeSwitchedAtRuntime() throws Exception {
        mvc.perform(get("/api/advisor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("RULES"))
                .andExpect(jsonPath("$.available.length()").value(2));

        mvc.perform(put("/api/advisor").contentType(MediaType.APPLICATION_JSON).content("{\"strategy\":\"AI\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("AI"));
        mvc.perform(put("/api/advisor").contentType(MediaType.APPLICATION_JSON).content("{\"strategy\":\"RULES\"}"))
                .andExpect(jsonPath("$.strategy").value("RULES"));

        mvc.perform(put("/api/advisor").contentType(MediaType.APPLICATION_JSON).content("{\"strategy\":\"MAGIC\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonNumericProductIdIsABadRequest() throws Exception {
        mvc.perform(get("/api/products/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        mvc.perform(get("/api/products/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product not found: 999999"));
    }

    private long createProduct(String sku, long stock) throws Exception {
        JsonNode created = body(post("/api/products", productJson(sku, stock))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location")));
        return created.get("id").asLong();
    }

    private static String productJson(String sku, long stock) {
        return """
                {"sku":"%s","name":"Test product","category":"ELECTRONICS","unitPrice":100.00,
                 "initialStock":%d,"lowStockThreshold":10,"safetyStock":3,"leadTimeDays":5}""".formatted(sku, stock);
    }

    private JsonNode awaitPendingReorder(long productId) throws Exception {
        for (int i = 0; i < 50; i++) {
            JsonNode list = body(mvc.perform(get("/api/suggestions")
                    .param("productId", String.valueOf(productId))
                    .param("type", "REORDER")
                    .param("status", "PENDING")));
            if (!list.isEmpty()) return list.get(0);
            Thread.sleep(100);
        }
        throw new AssertionError("No pending reorder suggestion appeared for product " + productId);
    }

    private ResultActions post(String url, String content) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(content));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
