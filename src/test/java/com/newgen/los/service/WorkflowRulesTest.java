package com.newgen.los.service;

import com.newgen.los.exception.InvalidTransitionException;
import com.newgen.los.model.ApplicationStatus;
import org.junit.jupiter.api.Test;

import static com.newgen.los.model.ApplicationStatus.APPROVED;
import static com.newgen.los.model.ApplicationStatus.CREDIT_APPROVAL;
import static com.newgen.los.model.ApplicationStatus.DISBURSED;
import static com.newgen.los.model.ApplicationStatus.DRAFT;
import static com.newgen.los.model.ApplicationStatus.EXIT;
import static com.newgen.los.model.ApplicationStatus.REJECTED;
import static com.newgen.los.model.ApplicationStatus.UNDER_REVIEW;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowRulesTest {

    @Test
    void normalFlowIsAllowed() {
        assertDoesNotThrow(() -> {
            WorkflowRules.ensure(DRAFT, UNDER_REVIEW);
            WorkflowRules.ensure(UNDER_REVIEW, CREDIT_APPROVAL);
            WorkflowRules.ensure(CREDIT_APPROVAL, APPROVED);
            WorkflowRules.ensure(APPROVED, DISBURSED);
            WorkflowRules.ensure(DISBURSED, ApplicationStatus.CORE_BANKING_PUSH);
            WorkflowRules.ensure(ApplicationStatus.CORE_BANKING_PUSH, EXIT);
        });
    }

    @Test
    void rejectionPaths() {
        assertTrue(WorkflowRules.canMove(DRAFT, REJECTED));
        assertTrue(WorkflowRules.canMove(UNDER_REVIEW, REJECTED));
        assertTrue(WorkflowRules.canMove(CREDIT_APPROVAL, REJECTED));
        assertFalse(WorkflowRules.canMove(APPROVED, REJECTED));
    }

    @Test
    void skippingStagesIsRejected() {
        assertThrows(InvalidTransitionException.class, () -> WorkflowRules.ensure(DRAFT, APPROVED));
        assertThrows(InvalidTransitionException.class, () -> WorkflowRules.ensure(DRAFT, DISBURSED));
        assertThrows(InvalidTransitionException.class, () -> WorkflowRules.ensure(UNDER_REVIEW, DISBURSED));
        assertThrows(InvalidTransitionException.class, () -> WorkflowRules.ensure(UNDER_REVIEW, APPROVED));
        assertThrows(InvalidTransitionException.class, () -> WorkflowRules.ensure(APPROVED, UNDER_REVIEW));
    }

    @Test
    void terminalStatesGoNowhere() {
        for (ApplicationStatus to : ApplicationStatus.values()) {
            assertFalse(WorkflowRules.canMove(REJECTED, to));
            assertFalse(WorkflowRules.canMove(EXIT, to));
        }
        assertTrue(WorkflowRules.isTerminal(REJECTED));
        assertTrue(WorkflowRules.isTerminal(EXIT));
        assertFalse(WorkflowRules.canMove(DISBURSED, UNDER_REVIEW));
        assertFalse(WorkflowRules.canMove(DISBURSED, APPROVED));
    }
}
