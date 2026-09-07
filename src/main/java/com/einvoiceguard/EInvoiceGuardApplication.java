package com.einvoiceguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class EInvoiceGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(EInvoiceGuardApplication.class, args);
    }
}
