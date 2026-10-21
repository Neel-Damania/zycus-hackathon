package com.zycus.hackathon.suggestion.advisor;

import java.util.List;

import com.zycus.hackathon.suggestion.signal.ProductContext;

/** Strategy contract for turning a product's situation into advice. Selected by {@link AdvisorEngine}. */
public interface Advisor {

    AdvisorStrategy strategy();

    /**
     * @param context          the product, its stock, demand and the trigger
     * @param recentRejections this product's latest rejected suggestions, newest first
     */
    Advice advise(ProductContext context, List<ReviewFeedback> recentRejections);
}
