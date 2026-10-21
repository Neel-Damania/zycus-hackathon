package com.zycus.hackathon.sales;

import java.util.List;

public record OrderResponse(List<OrderLineResult> items) {
}
