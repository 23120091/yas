package com.yas.payment;

import com.yas.commonlibrary.config.CorsConfig;
import com.yas.payment.config.ServiceUrlConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = {"com.yas.payment", "com.yas.commonlibrary"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.yas\\.payment\\.paypal\\.config\\..*"))
@EnableConfigurationProperties({ServiceUrlConfig.class, CorsConfig.class})
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
