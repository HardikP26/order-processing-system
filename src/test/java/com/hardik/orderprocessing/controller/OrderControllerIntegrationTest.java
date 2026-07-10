package com.hardik.orderprocessing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardik.orderprocessing.dto.CreateOrderRequest;
import com.hardik.orderprocessing.dto.OrderItemRequest;
import com.hardik.orderprocessing.dto.UpdateStatusRequest;
import com.hardik.orderprocessing.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    private static final String BASE = "/api/v1/orders";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private CreateOrderRequest sampleRequest() {
        return new CreateOrderRequest("Hardik Parmar",
                List.of(new OrderItemRequest("Wireless Mouse", 3, new BigDecimal("20.00"))));
    }

    @Test
    void createOrder_returns201WithPendingStatusAndComputedTotal() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(60.00))
                .andExpect(jsonPath("$.items[0].productName").value("Wireless Mouse"));
    }

    @Test
    void createOrder_withNoItems_returns400() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("Hardik Parmar", List.of());

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    void getOrder_afterCreation_returnsSameOrder() throws Exception {
        String body = mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(get(BASE + "/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.customerName").value("Hardik Parmar"));
    }

    @Test
    void getOrder_whenMissing_returns404() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    void cancelOrder_whenPending_returns200AndCancelledStatus() throws Exception {
        String body = mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(put(BASE + "/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_whenAlreadyCancelled_isIdempotentAndReturns200() throws Exception {
        String body = mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();
        mockMvc.perform(put(BASE + "/{id}/cancel", id)).andExpect(status().isOk());

        mockMvc.perform(put(BASE + "/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_whenShipped_returns409Conflict() throws Exception {
        String body = mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();
        mockMvc.perform(put(BASE + "/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateStatusRequest(OrderStatus.PROCESSING))));
        mockMvc.perform(put(BASE + "/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateStatusRequest(OrderStatus.SHIPPED))));

        mockMvc.perform(put(BASE + "/{id}/cancel", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void listOrders_filteredByStatus_returnsOnlyMatchingOrders() throws Exception {
        mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sampleRequest())));

        mockMvc.perform(get(BASE).param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void listOrders_withPagination_returnsRequestedPageSize() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post(BASE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sampleRequest())));
        }

        mockMvc.perform(get(BASE).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getOrderHistory_afterCreateAndCancel_recordsBothTransitions() throws Exception {
        String body = mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(body).get("id").asLong();
        mockMvc.perform(put(BASE + "/{id}/cancel", id));

        mockMvc.perform(get(BASE + "/{id}/history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].toStatus").value("PENDING"))
                .andExpect(jsonPath("$[1].toStatus").value("CANCELLED"));
    }
}
