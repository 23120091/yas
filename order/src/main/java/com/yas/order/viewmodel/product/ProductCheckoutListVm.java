package com.yas.order.viewmodel.product;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Builder(toBuilder = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductCheckoutListVm {
    Long id;
    String name;
    Double price;
    Long taxClassId;
}
