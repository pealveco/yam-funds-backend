package co.com.yam.funds.api;

import co.com.yam.funds.api.dto.ErrorResponse;
import co.com.yam.funds.api.dto.HealthResponse;
import co.com.yam.funds.api.dto.SubscribeToFundRequest;
import co.com.yam.funds.api.dto.SubscriptionResponse;
import co.com.yam.funds.api.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.bind.annotation.RequestMethod;

import static org.springframework.web.reactive.function.server.RequestPredicates.DELETE;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class RouterRest {
    @RouterOperations({
            @RouterOperation(path = "/api/health",
                    method = RequestMethod.GET,
                    beanClass = Handler.class,
                    beanMethod = "health",
                    operation = @Operation(
                            operationId = "getHealth",
                            summary = "Get API health status",
                            description = "Returns a lightweight health response for external checks.",
                            tags = {"Health"},
                            responses = {
                                    @ApiResponse(responseCode = "200",
                                            description = "API is available",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = HealthResponse.class),
                                                    examples = @ExampleObject(value = "{\"status\":\"UP\"}")))
                            })),
            @RouterOperation(path = "/api/clients/{clientId}/subscriptions",
                    method = RequestMethod.POST,
                    beanClass = Handler.class,
                    beanMethod = "subscribeToFund",
                    operation = @Operation(
                            operationId = "subscribeClientToFund",
                            summary = "Subscribe a client to a fund",
                            description = "Creates a fund subscription, debits the client balance, records the transaction and sends a best-effort notification.",
                            tags = {"Subscriptions"},
                            parameters = {
                                    @Parameter(name = "clientId",
                                            in = ParameterIn.PATH,
                                            required = true,
                                            description = "Client identifier",
                                            example = "client-001")
                            },
                            requestBody = @RequestBody(required = true,
                                    description = "Fund subscription request",
                                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                            schema = @Schema(implementation = SubscribeToFundRequest.class),
                                            examples = @ExampleObject(value = "{\"fundId\":\"1\"}"))),
                            responses = {
                                    @ApiResponse(responseCode = "201",
                                            description = "Subscription created",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = SubscriptionResponse.class))),
                                    @ApiResponse(responseCode = "400",
                                            description = "Client does not have enough balance",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponse.class))),
                                    @ApiResponse(responseCode = "404",
                                            description = "Client or fund was not found",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponse.class))),
                                    @ApiResponse(responseCode = "409",
                                            description = "Client is already subscribed or a concurrent write conflict occurred",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponse.class))),
                                    @ApiResponse(responseCode = "500",
                                            description = "Unexpected error",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponse.class)))
                            })),
            @RouterOperation(path = "/api/clients/{clientId}/subscriptions/{fundId}",
                    method = RequestMethod.DELETE,
                    beanClass = Handler.class,
                    beanMethod = "cancelSubscription",
                    operation = @Operation(
                            operationId = "cancelClientFundSubscription",
                            summary = "Cancel a fund subscription",
                            description = "Cancels an active subscription, returns the subscribed amount to the client balance and records the transaction.",
                            tags = {"Subscriptions"},
                            parameters = {
                                    @Parameter(name = "clientId",
                                            in = ParameterIn.PATH,
                                            required = true,
                                            description = "Client identifier",
                                            example = "client-001"),
                                    @Parameter(name = "fundId",
                                            in = ParameterIn.PATH,
                                            required = true,
                                            description = "Fund identifier",
                                            example = "1")
                            },
                            responses = {
                                    @ApiResponse(responseCode = "204",
                                            description = "Subscription cancelled"),
                                    @ApiResponse(responseCode = "404",
                                            description = "Client, fund or subscription was not found",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponse.class))),
                                    @ApiResponse(responseCode = "409",
                                            description = "Concurrent write conflict occurred",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponse.class))),
                                    @ApiResponse(responseCode = "500",
                                            description = "Unexpected error",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponse.class)))
                            })),
            @RouterOperation(path = "/api/clients/{clientId}/transactions",
                    method = RequestMethod.GET,
                    beanClass = Handler.class,
                    beanMethod = "getTransactionHistory",
                    operation = @Operation(
                            operationId = "getClientTransactionHistory",
                            summary = "Get client transaction history",
                            description = "Returns the client's fund subscription and cancellation transactions ordered from newest to oldest.",
                            tags = {"Transactions"},
                            parameters = {
                                    @Parameter(name = "clientId",
                                            in = ParameterIn.PATH,
                                            required = true,
                                            description = "Client identifier",
                                            example = "client-001")
                            },
                            responses = {
                                    @ApiResponse(responseCode = "200",
                                            description = "Transaction history",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    array = @ArraySchema(schema = @Schema(implementation = TransactionResponse.class)))),
                                    @ApiResponse(responseCode = "404",
                                            description = "Client was not found",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponse.class))),
                                    @ApiResponse(responseCode = "500",
                                            description = "Unexpected error",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                    schema = @Schema(implementation = ErrorResponse.class)))
                            }))
    })
    @Bean
    public RouterFunction<ServerResponse> routerFunction(Handler handler) {
        return route(GET("/api/health"), handler::health)
                .andRoute(POST("/api/clients/{clientId}/subscriptions"), handler::subscribeToFund)
                .andRoute(DELETE("/api/clients/{clientId}/subscriptions/{fundId}"), handler::cancelSubscription)
                .andRoute(GET("/api/clients/{clientId}/transactions"), handler::getTransactionHistory);
    }
}
