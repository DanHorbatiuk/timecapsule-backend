package dev.horbatiuk.timecapsule.service;

import dev.horbatiuk.timecapsule.exception.ConflictException;
import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.persistence.UserRepository;
import dev.horbatiuk.timecapsule.persistence.dto.user.UpdateUserInfoRequestDTO;
import dev.horbatiuk.timecapsule.persistence.entities.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public User findUserByEmail(String email) throws NotFoundException {
        return userRepository.findUserByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    public void updateProfile(String email, UpdateUserInfoRequestDTO requestDTO) throws NotFoundException, ConflictException {
        User user = userRepository.findUserByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (StringUtils.hasText(requestDTO.getName())) {
            user.setName(requestDTO.getName());
            logger.info("Changing user's ({}) name to {}", user.getId(), requestDTO.getName());
        }
        if (StringUtils.hasText(requestDTO.getNewPassword())) {
            if (!passwordEncoder.matches(requestDTO.getOldPassword(), user.getPassword())) {
                throw new ConflictException("Your old password does not match");
            }
            String newPasswordEncoded = passwordEncoder.encode(requestDTO.getNewPassword());
            user.setPassword(newPasswordEncoded);
            logger.info("Changing user's ({}) password", user.getId());
        }
        userRepository.save(user);
        logger.info("User's ({}) credentials changed success", user.getId());
    }

}
