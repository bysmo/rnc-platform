package bf.rnc.services.trust.id.service;

import bf.rnc.services.trust.id.entity.Citizen;
import bf.rnc.services.trust.id.entity.PepStatus;
import bf.rnc.services.trust.id.entity.SanctionsScreening;
import bf.rnc.services.trust.id.repository.SanctionsScreeningRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service de screening sanctions et PEP (conformité LCB-FT — Loi 049-2008/AN).
 *
 * <p>Au MVP, ce service simule les screenings. En production, il doit s'intégrer à :</p>
 * <ul>
 *   <li>Liste sanctions ONU (https://www.un.org/securitycouncil/content/un-sc-consolidated-list)</li>
 *   <li>Liste OFAC (US Treasury)</li>
 *   <li>Liste UE (EU consolidated financial sanctions list)</li>
 *   <li>Base PEP locale / World-Check / Refinitiv</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SanctionsScreeningService {

    private final SanctionsScreeningRepository screeningRepository;
    private final ObjectMapper objectMapper;

    /**
     * Effectue un screening complet pour un citoyen : sanctions ONU, OFAC, UE, et PEP.
     * Met à jour le statut du citoyen.
     */
    @Transactional
    public void screenCitizen(Citizen citizen) {
        log.info("Screening LCB-FT pour citoyen {}...", citizen.getCitizenReference());

        boolean anyHit = false;
        anyHit |= screenAgainstList(citizen, "UN_SANCTIONS", "United Nations Consolidated List");
        anyHit |= screenAgainstList(citizen, "OFAC", "US OFAC SDN List");
        anyHit |= screenAgainstList(citizen, "EU", "EU Consolidated Financial Sanctions");

        // PEP check
        boolean isPep = screenPep(citizen);

        citizen.setSanctionsScreened(true);
        citizen.setSanctionsScreenedAt(Instant.now());
        citizen.setSanctionsHit(anyHit);
        citizen.setPepStatus(isPep ? PepStatus.PEP : PepStatus.NOT_PEP);
        citizen.setPepVerifiedAt(Instant.now());

        if (anyHit) {
            log.warn("⚠️ Citoyen {} — SANCTIONS HIT détecté !", citizen.getCitizenReference());
        } else {
            log.info("✓ Citoyen {} — screening négatif", citizen.getCitizenReference());
        }
    }

    /**
     * Récupère l'historique des screenings pour audit.
     */
    public List<SanctionsScreening> getScreeningHistory(Citizen citizen) {
        return screeningRepository.findByCitizenIdOrderByScreenDateDesc(citizen.getId());
    }

    // ============================================================
    // Implémentation mock (à remplacer par vraies intégrations API)
    // ============================================================

    private boolean screenAgainstList(Citizen citizen, String listType, String listName) {
        // MOCK : 99,9% des citoyens ne sont pas sur les listes
        // En production : appel API dédiée avec fuzzy matching sur nom + date naissance
        boolean hit = false; // simulation

        SanctionsScreening screening = new SanctionsScreening();
        screening.setCitizenId(citizen.getId());
        screening.setScreeningType(listType);
        screening.setMatched(hit);
        screening.setScore(hit ? 95 : 0);
        screening.setProvider(listName);

        try {
            if (hit) {
                screening.setMatchedEntries(objectMapper.writeValueAsString(List.of(Map.of(
                    "name", "MATCH FOUND",
                    "list", listName,
                    "score", 95
                ))));
            }
            screening.setRawResponse(objectMapper.writeValueAsString(Map.of(
                "status", "completed",
                "list_size", 0,
                "match_score", hit ? 95 : 0
            )));
        } catch (Exception e) {
            log.warn("Erreur sérialisation JSON screening", e);
        }

        screeningRepository.save(screening);
        return hit;
    }

    private boolean screenPep(Citizen citizen) {
        // MOCK : vérification PEP
        // En production : interroger base PEP locale ou World-Check
        return false;
    }
}
