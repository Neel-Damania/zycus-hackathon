package com.zycus.hackathon.suggestion.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class AiResponseParserTest {

    private final AiResponseParser parser = new AiResponseParser(JsonMapper.builder().build());

    private static final String JSON =
            "{\"reorderQuantity\": 40, \"suggestedPrice\": 26.49, \"reasoning\": \"Low cover.\", \"confidence\": 0.8}";

    @Test
    void parsesPlainJson() {
        AiResponse response = parser.parse(JSON);

        assertThat(response.reorderQuantity()).isEqualTo(40);
        assertThat(response.suggestedPrice()).isEqualByComparingTo("26.49");
        assertThat(response.reasoning()).isEqualTo("Low cover.");
        assertThat(response.confidence()).isEqualTo(0.8);
    }

    @Test
    void toleratesCodeFencesAndSurroundingProse() {
        assertThat(parser.parse("```json\n" + JSON + "\n```").reorderQuantity()).isEqualTo(40);
        assertThat(parser.parse("Here is my advice: " + JSON + " Thanks.").reorderQuantity()).isEqualTo(40);
    }

    @Test
    void rejectsEmptyMissingOrBrokenJson() {
        assertThatThrownBy(() -> parser.parse("  ")).isInstanceOf(InvalidAdviceException.class);
        assertThatThrownBy(() -> parser.parse("no json here")).hasMessageContaining("no JSON object");
        assertThatThrownBy(() -> parser.parse("{\"reorderQuantity\": 40, ")).isInstanceOf(InvalidAdviceException.class);
        assertThatThrownBy(() -> parser.parse("{\"reorderQuantity\": \"many\"}"))
                .hasMessageContaining("unparseable");
    }
}
