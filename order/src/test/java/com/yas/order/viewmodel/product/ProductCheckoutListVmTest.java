package com.yas.order.viewmodel.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ProductCheckoutListVmTest {

    @Test
    void shouldDeserializeFromJsonObject() throws Exception {
        String json = """
            {
              "id": 1,
              "name": "Product A",
              "price": 99.5,
              "taxClassId": 2
            }
            """;

        ProductCheckoutListVm result = new ObjectMapper().readValue(json, ProductCheckoutListVm.class);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Product A");
        assertThat(result.getPrice()).isEqualTo(99.5);
        assertThat(result.getTaxClassId()).isEqualTo(2L);
    }
}
