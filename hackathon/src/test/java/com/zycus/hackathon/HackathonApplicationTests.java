package com.zycus.hackathon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.zycus.hackathon.product.ProductResponse;
import com.zycus.hackathon.product.ProductService;
import com.zycus.hackathon.suggestion.SuggestionResponse;
import com.zycus.hackathon.suggestion.SuggestionReviewService;
import com.zycus.hackathon.suggestion.SuggestionStatus;
import com.zycus.hackathon.suggestion.SuggestionType;
import com.zycus.hackathon.suggestion.signal.AlertType;

/**
 * The full application starts, seeds its demo data, and the first evaluations raise the alerts the
 * demo is built to show. Rules strategy, so no LLM call is made.
 */
@SpringBootTest(properties = {
        "stockpulse.advisor.strategy=RULES",
        "stockpulse.monitoring.enabled=false"
})
class HackathonApplicationTests {

    @Autowired
    private ProductService products;

    @Autowired
    private SuggestionReviewService suggestions;

    @Test
    void startsWithDemoDataThatTriggersBothAlerts() throws InterruptedException {
        List<ProductResponse> catalogue = products.list();
        assertThat(catalogue).extracting(ProductResponse::sku)
                .containsExactly("ELEC-MOUSE-01", "BOOK-CLEAN-01", "CLTH-TEE-01", "HOME-KNIFE-01");

        List<SuggestionResponse> pending = awaitPending(3);
        assertThat(pending)
                .anySatisfy(s -> {
                    assertThat(s.productId()).isEqualTo(idOf(catalogue, "BOOK-CLEAN-01"));
                    assertThat(s.alertType()).isEqualTo(AlertType.LOW_STOCK);
                    assertThat(s.type()).isEqualTo(SuggestionType.REORDER);
                })
                .anySatisfy(s -> {
                    assertThat(s.productId()).isEqualTo(idOf(catalogue, "ELEC-MOUSE-01"));
                    assertThat(s.alertType()).isEqualTo(AlertType.DEMAND_SPIKE);
                    assertThat(s.type()).isEqualTo(SuggestionType.PRICING);
                });
    }

    /** Demo evaluations run asynchronously after the seeding transaction commits. */
    private List<SuggestionResponse> awaitPending(int expected) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            List<SuggestionResponse> pending = suggestions.find(null, null, SuggestionStatus.PENDING);
            if (pending.size() >= expected) {
                return pending;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Demo data did not produce " + expected + " pending suggestions");
    }

    private static Long idOf(List<ProductResponse> catalogue, String sku) {
        return catalogue.stream().filter(p -> p.sku().equals(sku)).findFirst().orElseThrow().id();
    }
}
