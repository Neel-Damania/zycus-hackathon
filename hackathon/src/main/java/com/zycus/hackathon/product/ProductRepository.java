package com.zycus.hackathon.product;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);
}
