package gguip1.study.messagequeuestudy.service;

import gguip1.study.messagequeuestudy.domain.Coupon;
import gguip1.study.messagequeuestudy.domain.CouponIssue;
import gguip1.study.messagequeuestudy.repository.CouponIssueRepository;
import gguip1.study.messagequeuestudy.repository.CouponRepository;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@Service
public class CouponIssueService {

    public enum Result {
        ISSUED,
        SOLD_OUT,
        ALREADY_ISSUED,
        COUPON_NOT_FOUND
    }

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;

    public CouponIssueService(CouponRepository couponRepository, CouponIssueRepository couponIssueRepository) {
        this.couponRepository = couponRepository;
        this.couponIssueRepository = couponIssueRepository;
    }

    @Transactional
    public Result issueSync(Long userId, Long couponId) {
        Coupon coupon = couponRepository.findByIdForUpdate(couponId).orElse(null);
        if (coupon == null) return Result.COUPON_NOT_FOUND;

        if (coupon.soldOut()) return Result.SOLD_OUT;

        try {
            couponIssueRepository.save(new CouponIssue(couponId, userId));
        } catch (DataIntegrityViolationException e) {
            return Result.ALREADY_ISSUED;
        }

        coupon.increaseIssued();
        return Result.ISSUED;
    }

    @Transactional
    public Result issueSyncAtomic(Long userId, Long couponId) {
        int updated = couponRepository.tryIncreaseIssued(couponId);

        if (updated == 0) {
            return couponRepository.existsById(couponId) ? Result.SOLD_OUT : Result.COUPON_NOT_FOUND;
        }

        try {
            couponIssueRepository.save(new CouponIssue(couponId, userId));
            return Result.ISSUED;
        } catch (DataIntegrityViolationException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Result.ALREADY_ISSUED;
        }
    }
}
