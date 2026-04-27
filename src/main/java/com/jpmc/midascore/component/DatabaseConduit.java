package com.jpmc.midascore.component;

import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Incentive;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.repository.TransactionRepository;
import com.jpmc.midascore.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class DatabaseConduit {
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final RestTemplate restTemplate;
    private final String incentiveApiUrl;

    public DatabaseConduit(UserRepository userRepository,
                           TransactionRepository transactionRepository,
                           RestTemplateBuilder restTemplateBuilder,
                           @Value("${general.incentive-api-url}") String incentiveApiUrl) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.restTemplate = restTemplateBuilder.build();
        this.incentiveApiUrl = incentiveApiUrl;
    }

    public void save(UserRecord userRecord) {
        userRepository.save(userRecord);
    }

    public void process(Transaction transaction) {
        UserRecord sender = userRepository.findById(transaction.getSenderId());
        UserRecord recipient = userRepository.findById(transaction.getRecipientId());
        if (sender == null || recipient == null) {
            return;
        }
        if (sender.getBalance() < transaction.getAmount()) {
            return;
        }
        Incentive incentive = restTemplate.postForObject(incentiveApiUrl, transaction, Incentive.class);
        float incentiveAmount = incentive == null ? 0f : Math.max(0f, incentive.getAmount());
        sender.setBalance(sender.getBalance() - transaction.getAmount());
        recipient.setBalance(recipient.getBalance() + transaction.getAmount() + incentiveAmount);
        userRepository.save(sender);
        userRepository.save(recipient);
        transactionRepository.save(new TransactionRecord(sender, recipient, transaction.getAmount(), incentiveAmount));
    }
}
