package com.zycus.hackathon.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.zycus.hackathon.product.Category;
import com.zycus.hackathon.product.InventoryService;
import com.zycus.hackathon.product.StockSnapshot;

class SalesServiceTest {

    private final SaleRepository sales = mock(SaleRepository.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final SalesService service = new SalesService(sales, inventory);

    private static StockSnapshot stock(long productId, long onHand) {
        return new StockSnapshot(productId, "SKU-" + productId, "Product " + productId, Category.BOOKS,
                new BigDecimal("10.00"), onHand, 0, 5, 1, 3);
    }

    @Test
    void successfulLineTakesStockAndRecordsTheSaleAtTheCurrentPrice() {
        when(inventory.findSnapshot(1L)).thenReturn(Optional.of(stock(1, 20)));
        when(inventory.decreaseStock(1L, 4)).thenReturn(Optional.of(stock(1, 16)));

        OrderResponse response = service.placeOrder(new OrderRequest(List.of(new OrderItem(1L, 4))));

        assertThat(response.items()).singleElement().satisfies(line -> {
            assertThat(line.status()).isEqualTo(OrderLineStatus.SUCCESS);
            assertThat(line.remainingStock()).isEqualTo(16);
        });
        ArgumentCaptor<Sale> sale = ArgumentCaptor.forClass(Sale.class);
        verify(sales).save(sale.capture());
        assertThat(sale.getValue().getQuantity()).isEqualTo(4);
        assertThat(sale.getValue().getUnitPrice()).isEqualByComparingTo("10.00");
    }

    @Test
    void linesSucceedOrFailIndependently() {
        when(inventory.findSnapshot(1L)).thenReturn(Optional.of(stock(1, 2)));
        when(inventory.decreaseStock(1L, 5)).thenReturn(Optional.empty());     // short
        when(inventory.findSnapshot(2L)).thenReturn(Optional.of(stock(2, 10)));
        when(inventory.decreaseStock(2L, 3)).thenReturn(Optional.of(stock(2, 7)));
        when(inventory.findSnapshot(9L)).thenReturn(Optional.empty());         // unknown

        OrderResponse response = service.placeOrder(new OrderRequest(List.of(
                new OrderItem(1L, 5), new OrderItem(2L, 3), new OrderItem(9L, 1))));

        assertThat(response.items()).extracting(OrderLineResult::status)
                .containsExactly(OrderLineStatus.FAILED, OrderLineStatus.SUCCESS, OrderLineStatus.FAILED);
        assertThat(response.items()).extracting(OrderLineResult::message)
                .containsExactly("Insufficient stock for product: 1", "Product ordered successfully",
                        "Product not found: 9");
    }

    @Test
    void failedLinesRecordNoSale() {
        when(inventory.findSnapshot(1L)).thenReturn(Optional.of(stock(1, 2)));
        when(inventory.decreaseStock(1L, 5)).thenReturn(Optional.empty());

        service.placeOrder(new OrderRequest(List.of(new OrderItem(1L, 5))));

        verify(sales, never()).save(any());
    }
}
