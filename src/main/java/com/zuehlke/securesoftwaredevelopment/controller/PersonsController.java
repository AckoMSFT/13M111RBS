package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.config.AuditLogger;
import com.zuehlke.securesoftwaredevelopment.config.SecurityUtil;
import com.zuehlke.securesoftwaredevelopment.domain.Person;
import com.zuehlke.securesoftwaredevelopment.domain.Role;
import com.zuehlke.securesoftwaredevelopment.domain.User;
import com.zuehlke.securesoftwaredevelopment.repository.PersonRepository;
import com.zuehlke.securesoftwaredevelopment.repository.RoleRepository;
import com.zuehlke.securesoftwaredevelopment.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.sql.SQLException;
import java.util.List;

@Controller

public class PersonsController {

    private static final Logger LOG = LoggerFactory.getLogger(PersonsController.class);
    private static final AuditLogger auditLogger = AuditLogger.getAuditLogger(PersonRepository.class);

    private final PersonRepository personRepository;
    private final UserRepository userRepository;

    private final RoleRepository roleRepository;

    public PersonsController(PersonRepository personRepository, UserRepository userRepository, RoleRepository roleRepository) {
        this.personRepository = personRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    private boolean isAdmin(User user) {
        List<Role> roles = roleRepository.findByUserId(user.getId());
        if (roles == null) {
            return false;
        }
        boolean matched = roles.stream().anyMatch(role -> role.getName().equals("ADMIN"));
        LOG.info("isAdmin for user: " + user + " = " + matched);
        return matched;
    }

    @GetMapping("/persons/{id}")
    @PreAuthorize("hasAuthority('VIEW_PERSON')")
    public String person(@PathVariable int id, Model model, HttpSession httpSession) {
        model.addAttribute("CSRF_TOKEN", httpSession.getAttribute("CSRF_TOKEN"));
        model.addAttribute("person", personRepository.get("" + id));
        return "person";
    }

    @GetMapping("/myprofile")
    @PreAuthorize("hasAuthority('VIEW_MY_PROFILE')")
    public String self(Model model, Authentication authentication, HttpSession httpSession) {
        User user = (User) authentication.getPrincipal();
        model.addAttribute("CSRF_TOKEN", httpSession.getAttribute("CSRF_TOKEN"));
        model.addAttribute("person", personRepository.get("" + user.getId()));
        return "person";
    }

    @DeleteMapping("/persons/{id}")
    @PreAuthorize("hasAuthority('UPDATE_PERSON')")
    public ResponseEntity<Void> person(@PathVariable int id) throws AccessDeniedException {
        User user = SecurityUtil.getCurrentUser();
        if (!isAdmin(user) && user.getId() != id) {
            throw new AccessDeniedException("Cannot delete another user");
        }

        personRepository.delete(id);
        userRepository.delete(id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/update-person")
    @PreAuthorize("hasAuthority('UPDATE_PERSON')")
    public String updatePerson(
            Person person,
            HttpSession httpSession,
            @RequestParam("csrfToken") String csrfToken
    ) throws AccessDeniedException {
        String expectedCsrfToken = httpSession.getAttribute("CSRF_TOKEN").toString();
        if (!csrfToken.equals(expectedCsrfToken)) {
            throw new AccessDeniedException("Forbidden");
        }

        User user = SecurityUtil.getCurrentUser();
        boolean isSameUser = user.getId() == Integer.parseInt(person.getId());

        if (!isAdmin(user) && !isSameUser) {
            throw new AccessDeniedException("Cannot update another user");
        }

        personRepository.update(person);
        if (!isSameUser) {
            return "redirect:/persons/" + person.getId();
        } else {
            return "redirect:/myprofile";
        }
    }

    @GetMapping("/persons")
    @PreAuthorize("hasAuthority('VIEW_PERSONS_LIST')")
    public String persons(Model model) {
        model.addAttribute("persons", personRepository.getAll());
        return "persons";
    }

    @GetMapping(value = "/persons/search", produces = "application/json")
    @PreAuthorize("hasAuthority('VIEW_PERSONS_LIST')")
    @ResponseBody
    public List<Person> searchPersons(@RequestParam String searchTerm) throws SQLException {
        return personRepository.search(searchTerm);
    }
}
