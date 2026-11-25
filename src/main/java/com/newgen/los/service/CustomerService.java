package com.newgen.los.service;

import com.newgen.los.config.BusinessClock;
import com.newgen.los.dao.CustomerDao;
import com.newgen.los.dao.Database;
import com.newgen.los.dao.DuplicateKeyException;
import com.newgen.los.exception.BusinessRuleException;
import com.newgen.los.exception.NotFoundException;
import com.newgen.los.exception.ValidationException;
import com.newgen.los.model.Customer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class CustomerService {

    public static final Set<String> KYC_TYPES = Set.of("PAN", "AADHAAR", "PASSPORT", "VOTER_ID", "DRIVING_LICENCE");

    private final Database db;
    private final CustomerDao customerDao;
    private final BusinessClock clock;

    public CustomerService(Database db, CustomerDao customerDao, BusinessClock clock) {
        this.db = db;
        this.customerDao = customerDao;
        this.clock = clock;
    }

    public Customer create(Customer input) {
        normalise(input);
        validate(input);
        input.setCreatedAt(clock.now());
        try {
            long id = db.inTransaction(c -> {
                Optional<Customer> existing = customerDao.findByMobile(c, input.getMobile());
                if (existing.isPresent()) {
                    throw duplicateMobile(input.getMobile(), existing.get());
                }
                return customerDao.insert(c, input);
            });
            input.setCustomerId(id);
            return input;
        } catch (DuplicateKeyException e) {
            throw new BusinessRuleException("Mobile " + input.getMobile() + " is already registered");
        }
    }

    public Customer update(Customer input) {
        if (input.getCustomerId() == null) {
            throw new ValidationException("Customer id is required");
        }
        normalise(input);
        validate(input);
        input.setUpdatedAt(clock.now());
        try {
            db.inTransaction(c -> {
                customerDao.findById(c, input.getCustomerId())
                        .orElseThrow(() -> new NotFoundException("Customer " + input.getCustomerId() + " not found"));
                Optional<Customer> sameMobile = customerDao.findByMobile(c, input.getMobile());
                if (sameMobile.isPresent() && !sameMobile.get().getCustomerId().equals(input.getCustomerId())) {
                    throw duplicateMobile(input.getMobile(), sameMobile.get());
                }
                return customerDao.update(c, input);
            });
            return get(input.getCustomerId());
        } catch (DuplicateKeyException e) {
            throw new BusinessRuleException("Mobile " + input.getMobile() + " is already registered");
        }
    }

    public Customer get(long id) {
        return db.withConnection(c -> customerDao.findById(c, id))
                .orElseThrow(() -> new NotFoundException("Customer " + id + " not found"));
    }

    public List<Customer> search(String by, String value) {
        if (value == null || value.isBlank()) {
            return db.withConnection(c -> customerDao.findRecent(c, 50));
        }
        String term = value.trim();
        String mode = by == null ? "" : by.trim().toUpperCase();
        if (mode.isEmpty()) {
            mode = term.matches("\\d{10}") ? "MOBILE" : term.matches("\\d+") ? "ID" : "NAME";
        }
        switch (mode) {
            case "ID":
                if (!term.matches("\\d+")) {
                    throw new ValidationException("Customer id must be a number");
                }
                return db.withConnection(c -> customerDao.findById(c, Long.parseLong(term)))
                        .map(List::of).orElse(List.of());
            case "MOBILE":
                return db.withConnection(c -> customerDao.findByMobile(c, term))
                        .map(List::of).orElse(List.of());
            case "NAME":
                return db.withConnection(c -> customerDao.searchByName(c, term));
            default:
                throw new ValidationException("Search by ID, NAME or MOBILE");
        }
    }

    private static BusinessRuleException duplicateMobile(String mobile, Customer owner) {
        return new BusinessRuleException("Mobile " + mobile + " is already registered to customer "
                + owner.getCustomerId() + " (" + owner.getName() + ")");
    }

    private static void normalise(Customer c) {
        c.setName(trim(c.getName()));
        c.setMobile(trim(c.getMobile()));
        c.setEmail(trim(c.getEmail()));
        c.setAddress(trim(c.getAddress()));
        c.setKycType(c.getKycType() == null || c.getKycType().isBlank() ? null : c.getKycType().trim().toUpperCase());
        c.setKycNumber(c.getKycNumber() == null || c.getKycNumber().isBlank() ? null : c.getKycNumber().trim().toUpperCase());
    }

    private void validate(Customer c) {
        List<String> errors = new ArrayList<>();
        if (c.getName() == null || c.getName().length() < 2) {
            errors.add("Name is required");
        } else if (c.getName().length() > 100 || !c.getName().matches("[\\p{L} .'-]+")) {
            errors.add("Name can contain only letters, spaces, dot, apostrophe and hyphen");
        }
        if (c.getDateOfBirth() == null) {
            errors.add("Date of birth is required");
        } else if (c.getDateOfBirth().isAfter(clock.today())) {
            errors.add("Date of birth cannot be in the future");
        }
        if (c.getMobile() == null || !c.getMobile().matches("\\d{10}")) {
            errors.add("Mobile must contain exactly 10 digits");
        }
        if (c.getEmail() != null && !c.getEmail().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            errors.add("Email address is not valid");
        }
        if (c.getKycType() != null && !KYC_TYPES.contains(c.getKycType())) {
            errors.add("KYC type must be one of " + String.join(", ", KYC_TYPES));
        }
        if ("PAN".equals(c.getKycType()) && c.getKycNumber() != null && !c.getKycNumber().matches("[A-Z]{5}\\d{4}[A-Z]")) {
            errors.add("PAN must look like ABCDE1234F");
        }
        if ("AADHAAR".equals(c.getKycType()) && c.getKycNumber() != null && !c.getKycNumber().matches("\\d{12}")) {
            errors.add("Aadhaar number must have 12 digits");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
