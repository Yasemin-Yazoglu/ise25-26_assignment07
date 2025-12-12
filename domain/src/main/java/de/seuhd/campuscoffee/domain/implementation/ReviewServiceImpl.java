package de.seuhd.campuscoffee.domain.implementation;

import de.seuhd.campuscoffee.domain.configuration.ApprovalConfiguration;
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException;
import de.seuhd.campuscoffee.domain.exceptions.ValidationException;
import de.seuhd.campuscoffee.domain.model.objects.Pos;
import de.seuhd.campuscoffee.domain.model.objects.Review;
import de.seuhd.campuscoffee.domain.model.objects.User;
import de.seuhd.campuscoffee.domain.ports.api.ReviewService;
import de.seuhd.campuscoffee.domain.ports.data.CrudDataService;
import de.seuhd.campuscoffee.domain.ports.data.PosDataService;
import de.seuhd.campuscoffee.domain.ports.data.ReviewDataService;
import de.seuhd.campuscoffee.domain.ports.data.UserDataService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.NotDirectoryException;
import java.util.List;

/**
 * Implementation of the Review service that handles business logic related to review entities.
 */
@Slf4j
@Service
public class ReviewServiceImpl extends CrudServiceImpl<Review, Long> implements ReviewService {
    private final ReviewDataService reviewDataService;
    private final UserDataService userDataService;
    private final PosDataService posDataService;
    // TODO: Try to find out the purpose of this class and how it is connected to the application.yaml configuration file.
    private final ApprovalConfiguration approvalConfiguration;

    public ReviewServiceImpl(@NonNull ReviewDataService reviewDataService,
                             @NonNull UserDataService userDataService,
                             @NonNull PosDataService posDataService,
                             @NonNull ApprovalConfiguration approvalConfiguration) {
        super(Review.class);
        this.reviewDataService = reviewDataService;
        this.userDataService = userDataService;
        this.posDataService = posDataService;
        this.approvalConfiguration = approvalConfiguration;
    }

    @Override
    protected CrudDataService<Review, Long> dataService() {
        return reviewDataService;
    }

    @Override
    @Transactional
    public @NonNull Review upsert(@NonNull Review review) {
        // TODO: Implement the missing business logic here
        // Check if POS exists
        Long posId = review.pos().getId();
        if(posId == null) {
            throw new ValidationException("POS does not exist");
        }
        posDataService.getById(posId);

        // Check if user exists
        Long userId = review.author().getId();
        if(userId == null) {
            throw new ValidationException("User does not exist");
        }
        userDataService.getById(userId);

        // Check if the user already reviewed the POS
        List<Review> UserPosReviews = reviewDataService.filter(review.pos(), review.author());
        if(UserPosReviews.isEmpty()) {
            return super.upsert(review);
        }
        else {
            throw new ValidationException("User cannot write multiple reviews for the same POS.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<Review> filter(@NonNull Long posId, @NonNull Boolean approved) {
        return reviewDataService.filter(posDataService.getById(posId), approved);
    }

    @Override
    @Transactional
    public @NonNull Review approve(@NonNull Review review, @NonNull Long userId) {
        log.info("Processing approval request for review with ID '{}' by user with ID '{}'...",
                review.getId(), userId);

        // validate that the user exists
        // TODO: Implement the required business logic here
        userDataService.getById(userId);

        // validate that the review exists
        // TODO: Implement the required business logic here
        if(review.id() == null) {
            throw new ValidationException("Review does not exist");
        }
        try {
            reviewDataService.getById(review.getId());
        }
        catch (NotFoundException nfe) {
            throw new ValidationException("Review does not exist");
        }

        // a user cannot approve their own review
        // TODO: Implement the required business logic here
        if(userId.equals(review.author().getId())) {
            throw  new ValidationException("User cannot approve their own review.");
        }

        // increment approval count
        // TODO: Implement the required business logic here
        Review updated = review.toBuilder()
                .approvalCount(review.approvalCount() + 1)
                .build();

        // update approval status to determine if the review now reaches the approval quorum
        // TODO: Implement the required business logic here
        updated = updateApprovalStatus(updated);

        return reviewDataService.upsert(updated);
    }

    /**
     * Calculates and updates the approval status of a review based on the approval count.
     * Business rule: A review is approved when it reaches the configured minimum approval count threshold.
     *
     * @param review The review to calculate approval status for
     * @return The review with updated approval status
     */
    Review updateApprovalStatus(Review review) {
        log.debug("Updating approval status of review with ID '{}'...", review.getId());
        return review.toBuilder()
                .approved(isApproved(review))
                .build();
    }
    
    /**
     * Determines if a review meets the minimum approval threshold.
     * 
     * @param review The review to check
     * @return true if the review meets or exceeds the minimum approval count, false otherwise
     */
    private boolean isApproved(Review review) {
        return review.approvalCount() >= approvalConfiguration.minCount();
    }
}
