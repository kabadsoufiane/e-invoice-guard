# E-Invoice Guard

API REST de validation et de génération de factures électroniques conformes : **Factur-X**, **ZUGFeRD**, **XRechnung** et **Peppol BIS 3 / EN 16931**, avec production de PDF/A-3 hybrides prêts à transmettre.

Conçue pour être publiée en tant que produit sur [RapidAPI](https://rapidapi.com), mais fonctionne aussi bien en appel direct (marque blanche, intégration interne, tests).

## Pourquoi cette API

La facturation électronique devient obligatoire pour les entreprises françaises (réforme e-invoicing / e-reporting) et pour de nombreux marchés publics européens (Peppol). Or produire et valider un PDF/A-3 Factur-X correctement (police embarquée, XML CII structuré, profil conforme) est un vrai casse-tête technique. E-Invoice Guard s'occupe de cette complexité :

- **Valider** un PDF Factur-X/ZUGFeRD existant et obtenir un verdict détaillé (pas juste vrai/faux : quel profil, quel format, quelles erreurs précises).
- **Générer** une facture Factur-X conforme à partir d'un simple JSON métier (numéro, parties, lignes, montants) — le PDF visuel et le XML structuré sont produits et assemblés automatiquement.
- **Convertir** une facture existante vers un autre profil Factur-X.
- **Pré-valider** SIRET/SIREN et TVA française avant transmission.
- **Traiter en masse** plusieurs factures en une seule requête.
- **Tracer** la version des règles de validation utilisées, pour l'auditabilité.

## Sommaire

- [Démarrage rapide](#démarrage-rapide)
- [Authentification](#authentification)
- [Endpoints](#endpoints)
  - [POST /v1/build/facturx](#post-v1buildfacturx)
  - [POST /v1/validate/pdf](#post-v1validatepdf)
  - [POST /v1/validate](#post-v1validate)
  - [POST /v1/convert](#post-v1convert)
  - [POST /v1/prevalidate/fr](#post-v1prevalidatefr)
  - [POST /v1/validate/batch](#post-v1validatebatch)
  - [GET /v1/rulesets](#get-v1rulesets)
- [Gestion des erreurs](#gestion-des-erreurs)
- [Exemples prêts à l'emploi](#exemples-prêts-à-lemploi)
- [Cas d'usage](#cas-dusage)
- [Documentation interactive](#documentation-interactive)
- [Configuration](#configuration)
- [Lancer le projet en local](#lancer-le-projet-en-local)
- [Exécuter les tests](#exécuter-les-tests)
- [Architecture technique](#architecture-technique)
- [Licences des composants tiers](#licences-des-composants-tiers)

## Démarrage rapide

```bash
curl -X POST https://VOTRE_INSTANCE/v1/build/facturx \
  -H "Content-Type: application/json" \
  -H "X-API-Key: VOTRE_CLE" \
  -d '{
    "invoiceNumber": "FA-2026-0042",
    "issueDate": "2026-09-06",
    "sellerName": "Kabad Digital Services",
    "sellerVatNumber": "FR12345678901",
    "buyerName": "Client Test SARL",
    "buyerVatNumber": "FR98765432109",
    "currency": "EUR",
    "profile": "EN16931",
    "lines": [
      {"description": "Prestation de developpement backend", "quantity": 5, "unitPrice": 450.00, "vatRate": 20},
      {"description": "Licence API - abonnement mensuel", "quantity": 1, "unitPrice": 29.00, "vatRate": 20}
    ]
  }' \
  --output facture.pdf
```

Le fichier `facture.pdf` produit est un PDF/A-3 hybride : lisible visuellement par un humain, et embarquant le XML CII structuré exploitable par n'importe quel logiciel comptable conforme Factur-X.

## Authentification

Deux mécanismes d'authentification coexistent, à des niveaux différents :

| Mécanisme | Header | Rôle | Configuration |
|---|---|---|---|
| Clé API client | `X-API-Key` | Identifie et autorise chaque client final, indépendamment de RapidAPI | Variable d'environnement `EINVOICEGUARD_API_KEYS` (liste de clés séparées par des virgules) |
| Secret proxy RapidAPI | `X-RapidAPI-Proxy-Secret` | Garantit que l'appel transite bien par la plateforme RapidAPI (anti-contournement des quotas) | Variable d'environnement `EINVOICEGUARD_PROXY_SECRET`, configurée aussi côté tableau de bord RapidAPI ("Deploy your API" > "Add a secret header") |

**Comportement par défaut (aucune variable définie) : les deux filtres sont permissifs**, ce qui facilite les tests locaux. En production, définissez au moins `EINVOICEGUARD_API_KEYS` pour exiger une clé sur chaque appel.

Chemins toujours exemptés d'authentification : `/`, `/swagger-ui.html`, `/v3/api-docs`, `/actuator/health`, ainsi que les requêtes `OPTIONS` (pré-vol CORS).

Exemple de rejet sans clé API (si des clés sont configurées) :

```json
{
  "error": "UNAUTHORIZED",
  "message": "Cle API manquante ou invalide. Fournissez une cle valide dans l'en-tete X-API-Key."
}
```

## Endpoints

### POST /v1/build/facturx

Génère un PDF/A-3 hybride Factur-X à partir de données métier JSON. Le PDF visuel (en-tête, tableau des lignes, totaux, mentions légales) est dessiné automatiquement, puis le XML CII structuré y est embarqué selon le profil demandé.

**Corps de la requête** (`application/json`) :

| Champ | Type | Obligatoire | Contrainte |
|---|---|---|---|
| `invoiceNumber` | string | oui | max 100 caractères |
| `issueDate` | string | oui | format ISO `yyyy-MM-dd`, date calendaire valide, dans une plage de -20 ans à +5 ans |
| `sellerName` | string | oui | max 200 caractères |
| `sellerVatNumber` | string | oui | numéro de TVA intracommunautaire UE valide (ex. `FR12345678901`) |
| `buyerName` | string | oui | max 200 caractères |
| `buyerVatNumber` | string | non | numéro de TVA UE valide si fourni, sinon vide |
| `currency` | string | oui | code ISO 4217, 3 lettres majuscules (ex. `EUR`, `USD`, `CHF`) |
| `profile` | string | oui | l'une des valeurs `MINIMUM`, `BASIC`, `EN16931`, `EXTENDED` |
| `lines` | array | oui | 1 à 500 lignes (voir ci-dessous) |

Chaque élément de `lines` :

| Champ | Type | Obligatoire | Contrainte |
|---|---|---|---|
| `description` | string | oui | max 500 caractères |
| `quantity` | number | oui | strictement positif |
| `unitPrice` | number | oui | positif ou nul |
| `vatRate` | number | oui | entre 0 et 100 |

**Réponse en cas de succès** : `200 OK`, `Content-Type: application/pdf`, avec en-tête `Content-Disposition: attachment; filename="<invoiceNumber>.pdf"`.

**Réponses d'erreur** :
- `400 Bad Request` : un ou plusieurs champs invalides (voir [Gestion des erreurs](#gestion-des-erreurs)).
- `422 Unprocessable Entity` : champs syntaxiquement valides mais impossibles à traiter (ex. incohérence interne).
- `401 Unauthorized` : clé API manquante ou invalide (si l'authentification est active).

### POST /v1/validate/pdf

Valide un fichier PDF Factur-X/ZUGFeRD existant, envoyé en upload multipart. Extrait le XML embarqué et vérifie sa conformité EN 16931 / Factur-X / ZUGFeRD / XRechnung, ainsi que la conformité PDF/A-3 sous-jacente.

**Corps de la requête** (`multipart/form-data`) :

| Champ | Type | Description |
|---|---|---|
| `file` | fichier | Le PDF à valider (10 Mo max) |

**Exemple** :

```bash
curl -X POST https://VOTRE_INSTANCE/v1/validate/pdf \
  -H "X-API-Key: VOTRE_CLE" \
  -F "file=@facture.pdf"
```

**Réponse** (`200 OK`) :

```json
{
  "valid": true,
  "detectedProfile": "EN16931",
  "detectedFormat": "CII",
  "errors": [],
  "warnings": []
}
```

Si le document n'est pas conforme, `valid` passe à `false` et `errors` liste chaque anomalie (`ruleId`, `severity`, `message`, `xpath`, `line` quand disponibles). Un fichier illisible ou qui n'est pas un PDF renvoie également `valid: false` avec une erreur `FATAL`.

### POST /v1/validate

Valide directement un JSON métier de facture, sans générer de PDF. Applique exactement les mêmes règles de validation que `/v1/build/facturx` (TVA, dates, devise, profil, lignes), mais sans consommer le coût de génération PDF. Utile pour un contrôle rapide côté client avant l'appel coûteux de génération.

**Corps de la requête** (`application/json`) : identique à `/v1/build/facturx` (voir ci-dessus).

**Exemple** :

```bash
curl -X POST https://VOTRE_INSTANCE/v1/validate \
  -H "Content-Type: application/json" \
  -H "X-API-Key: VOTRE_CLE" \
  -d '{
    "invoiceNumber": "FA-2026-0042",
    "issueDate": "2026-09-06",
    "sellerName": "Kabad Digital Services",
    "sellerVatNumber": "FR12345678901",
    "buyerName": "Client Test SARL",
    "buyerVatNumber": "FR98765432109",
    "currency": "EUR",
    "profile": "EN16931",
    "lines": [
      {"description": "Prestation de developpement backend", "quantity": 5, "unitPrice": 450.00, "vatRate": 20}
    ]
  }'
```

**Réponse** (`200 OK`) :

```json
{
  "valid": true,
  "detectedProfile": "EN16931",
  "detectedFormat": "CII",
  "errors": [],
  "warnings": []
}
```

**Réponses d'erreur** : `400 Bad Request` si un ou plusieurs champs sont invalides (même structure détaillée que pour `/v1/build/facturx`).

### POST /v1/convert

Convertit une facture Factur-X/ZUGFeRD existante vers un autre profil (ex. `BASIC` vers `EN16931`). Le PDF source sert lui-même de gabarit visuel : les données métier sont extraites du XML CII déjà embarqué, puis réinjectées dans un nouveau PDF/A-3 conforme au profil cible.

**Corps de la requête** (`multipart/form-data`) :

| Champ | Type | Description |
|---|---|---|
| `file` | fichier | Le PDF Factur-X/ZUGFeRD source (10 Mo max) |
| `targetProfile` | string | Le profil cible : `MINIMUM`, `BASIC`, `EN16931` ou `EXTENDED` |

**Exemple** :

```bash
curl -X POST https://VOTRE_INSTANCE/v1/convert \
  -H "X-API-Key: VOTRE_CLE" \
  -F "file=@facture-basic.pdf" \
  -F "targetProfile=EN16931" \
  --output facture-en16931.pdf
```

**Réponse en cas de succès** : `200 OK`, `Content-Type: application/pdf`, PDF/A-3 regenere dans le profil cible.

**Réponses d'erreur** :
- `400 Bad Request` : profil cible invalide.
- `422 Unprocessable Entity` : PDF source illisible ou non reconnu comme Factur-X/ZUGFeRD.

### POST /v1/prevalidate/fr

Pré-validation spécifique France : vérifie le format et la clé de contrôle (algorithme de Luhn) d'un SIRET/SIREN, ainsi que la cohérence syntaxique d'un numéro de TVA intracommunautaire français, avant transmission de la facture. Vérification syntaxique uniquement (pas d'appel à l'API INSEE Sirene ni au service VIES européen).

**Corps de la requête** (`application/json`) :

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| `siret` | string | non* | SIRET/SIREN à vérifier (14 chiffres) |
| `vatNumber` | string | non* | Numéro de TVA à vérifier (ex. `FR12345678901`) |

\* Au moins un des deux champs doit être fourni.

**Exemple** :

```bash
curl -X POST https://VOTRE_INSTANCE/v1/prevalidate/fr \
  -H "Content-Type: application/json" \
  -H "X-API-Key: VOTRE_CLE" \
  -d '{"siret": "73282932000074", "vatNumber": "FR12345678901"}'
```

**Réponse** (`200 OK`) :

```json
{
  "valid": true,
  "siret": {
    "value": "73282932000074",
    "valid": true,
    "message": "SIRET valide (14 chiffres, cle de controle Luhn correcte)."
  },
  "vatNumber": {
    "value": "FR12345678901",
    "valid": true,
    "message": "Numero de TVA syntaxiquement valide (format europeen standard)."
  },
  "warnings": []
}
```

**Réponses d'erreur** : `422 Unprocessable Entity` si ni `siret` ni `vatNumber` ne sont fournis.

### POST /v1/validate/batch

Valide plusieurs PDF Factur-X/ZUGFeRD en une seule requête (traitement par lot). Chaque fichier est validé indépendamment : un fichier illisible ou non conforme n'interrompt pas le traitement des autres. Limite : 50 fichiers par requête.

**Corps de la requête** (`multipart/form-data`) :

| Champ | Type | Description |
|---|---|---|
| `files` | fichiers (multiple) | Les PDF à valider (10 Mo max par fichier, 50 fichiers max) |

**Exemple** :

```bash
curl -X POST https://VOTRE_INSTANCE/v1/validate/batch \
  -H "X-API-Key: VOTRE_CLE" \
  -F "files=@facture1.pdf" \
  -F "files=@facture2.pdf" \
  -F "files=@facture3.pdf" | jq
```

**Réponse** (`200 OK`) :

```json
[
  {
    "filename": "facture1.pdf",
    "result": {"valid": true, "detectedProfile": "EN16931", "detectedFormat": "CII", "errors": [], "warnings": []}
  },
  {
    "filename": "facture2.pdf",
    "result": {"valid": false, "detectedProfile": "UNKNOWN", "detectedFormat": "UNKNOWN", "errors": [{"ruleId": "IRRECOVERABLE", "severity": "FATAL", "message": "Fichier illisible", "xpath": null, "line": null}], "warnings": []}
  }
]
```

**Réponses d'erreur** : `400 Bad Request` si aucun fichier n'est envoyé ou si le lot dépasse 50 fichiers.

### GET /v1/rulesets

Renvoie la version des jeux de règles de validation actuellement actifs (utile pour la traçabilité d'audit : savoir quelle version des règles EN 16931 / Peppol a validé une facture donnée).

```bash
curl https://VOTRE_INSTANCE/v1/rulesets
```

Réponse (`200 OK`, texte brut) : `"EN16931-2025.1 / PEPPOL-BIS3-2026.05"`

## Gestion des erreurs

Toute erreur de validation métier sur `/v1/build/facturx` ou `/v1/validate` renvoie `400 Bad Request` avec une structure détaillée par champ :

```json
{
  "error": "INVALID_REQUEST",
  "message": "La requete contient 2 champ(s) invalide(s). Voir 'details'.",
  "details": [
    {
      "field": "sellerVatNumber",
      "message": "Numero de TVA du vendeur invalide (format attendu : ex. FR12345678901)",
      "rejectedValue": "INVALID123"
    },
    {
      "field": "lines[0].vatRate",
      "message": "Le taux de TVA ne peut pas depasser 100%",
      "rejectedValue": "150"
    }
  ]
}
```

Chaque entrée de `details` identifie précisément le champ fautif (y compris l'index de ligne, ex. `lines[0].quantity`), le message d'erreur, et la valeur rejetée — pas besoin de deviner quel champ pose problème.

| Code HTTP | Signification |
|---|---|
| `400` | Un ou plusieurs champs de la requête sont invalides |
| `401` | Clé API manquante ou invalide |
| `403` | Appel direct rejeté par le filtre proxy RapidAPI (si activé) |
| `422` | Requête syntaxiquement valide mais impossible à traiter |
| `429` | Quota de requêtes dépassé (géré par RapidAPI) |

## Exemples prêts à l'emploi

### Générer une facture minimale

```bash
curl -X POST https://VOTRE_INSTANCE/v1/build/facturx \
  -H "Content-Type: application/json" \
  -H "X-API-Key: VOTRE_CLE" \
  -d '{
    "invoiceNumber": "FA-2026-0001",
    "issueDate": "2026-09-06",
    "sellerName": "Ma Societe SAS",
    "sellerVatNumber": "FR11223344556",
    "buyerName": "Client Particulier",
    "buyerVatNumber": null,
    "currency": "EUR",
    "profile": "MINIMUM",
    "lines": [
      {"description": "Consultation", "quantity": 1, "unitPrice": 100.00, "vatRate": 20}
    ]
  }' \
  --output facture-minimale.pdf
```

### Valider une facture reçue d'un fournisseur

```bash
curl -X POST https://VOTRE_INSTANCE/v1/validate/pdf \
  -H "X-API-Key: VOTRE_CLE" \
  -F "file=@facture-fournisseur.pdf" | jq
```

### Collection Postman / Insomnia

Le JSON OpenAPI complet, importable directement dans Postman ou Insomnia, est disponible sur :

```
GET /v3/api-docs
```

## Cas d'usage

- **Éditeurs de logiciels de facturation** : ajouter la conformité Factur-X à un ERP ou logiciel de facturation existant sans réimplémenter le format CII et la génération PDF/A-3.
- **Plateformes SaaS B2B** : valider automatiquement les factures reçues de fournisseurs avant intégration comptable, y compris en masse via `/v1/validate/batch`.
- **Cabinets d'expertise-comptable** : vérifier en masse la conformité des factures de leurs clients avant la bascule vers la facturation électronique obligatoire.
- **Marketplaces et places de marché** : générer des factures conformes pour le compte de vendeurs tiers, et les pré-valider (SIRET/TVA) via `/v1/prevalidate/fr`.
- **Intégrateurs Peppol** : contrôler la conformité EN 16931 avant transmission sur le réseau Peppol, ou convertir un profil existant vers `EN16931` via `/v1/convert`.

## Documentation interactive

Une fois l'application démarrée :

- Interface Swagger UI : `http://localhost:8080/swagger-ui.html`
- JSON OpenAPI brut : `http://localhost:8080/v3/api-docs`

## Configuration

Toute la configuration se fait via `src/main/resources/application.yml` et des variables d'environnement (aucun secret n'est codé en dur).

| Variable d'environnement | Rôle | Défaut |
|---|---|---|
| `EINVOICEGUARD_API_KEYS` | Clés API valides, séparées par des virgules | vide (pas de contrôle) |
| `EINVOICEGUARD_PROXY_SECRET` | Secret partagé avec RapidAPI | vide (pas de contrôle) |
| `SERVER_PORT` | Port d'écoute HTTP | `8080` |

## Lancer le projet en local

Prérequis : JDK 21, Maven 3.9+.

```bash
export JAVA_HOME=/chemin/vers/jdk-21
mvn spring-boot:run
```

Avec une clé API activée pour les tests :

```bash
EINVOICEGUARD_API_KEYS=ma-cle-de-test mvn spring-boot:run
```

L'application écoute par défaut sur `http://localhost:8080`.

## Exécuter les tests

La suite de tests couvre les validateurs métier (unitaires), les services de génération/validation/conversion PDF (unitaires), et les 7 endpoints de bout en bout (intégration Spring Boot + MockMvc, avec et sans authentification par clé API) :

```bash
mvn test
```

## Architecture technique

| Composant | Rôle |
|---|---|
| Spring Boot 3.5 (Java 21) | Serveur d'API REST |
| Apache PDFBox 3.x | Génération de la mise en page visuelle du PDF (PDF/A-1b, polices embarquées) |
| mustangproject | Embarquement du XML CII, conversion de profil et validation Factur-X/ZUGFeRD (inclut veraPDF pour le contrôle PDF/A-3) |
| Bean Validation (Hibernate Validator) | Validation métier déclarative (TVA, SIRET, dates, montants) |
| springdoc-openapi | Documentation interactive Swagger UI / OpenAPI |
| Caffeine | Cache en mémoire pour les rulesets (6h de durée de vie) |

Le pipeline de génération se déroule en deux temps :
1. `InvoicePdfLayoutService` (PDFBox) dessine un PDF/A-1b visuel de base (en-tête, tableau, totaux, polices Liberation Sans réellement embarquées).
2. `FacturXBuilderService` (mustangproject) embarque le XML CII structuré dans ce PDF de base pour produire le PDF/A-3 hybride final.

La validation suit le chemin inverse : `FacturXValidationService` extrait le XML embarqué et le profil déclaré, puis délègue le contrôle de conformité EN 16931 / Peppol / PDF-A3 à mustangproject (veraPDF en interne).

La conversion de profil (`/v1/convert`) réutilise `FacturXBuilderService` : le PDF source sert lui-même de gabarit visuel, mustangproject en extrait l'objet métier `Invoice` via le XML CII déjà embarqué, puis réexporte ce même PDF avec le profil cible demandé — sans jamais redemander les données métier à l'appelant.

La pré-validation France (`/v1/prevalidate/fr`) est un service dédié (`FrPrevalidationService`) qui applique les mêmes règles que les validateurs `SiretValidator` et `VatNumberValidator` utilisés par Bean Validation, sans appel réseau externe (ni API INSEE Sirene, ni service VIES).

## Licences des composants tiers

- Apache PDFBox : licence Apache 2.0.
- mustangproject : licence Apache 2.0.
- Polices Liberation Sans (embarquées dans les PDF générés) : licence SIL Open Font License 1.1, libre de droits pour l'embarquement — voir `src/main/resources/fonts/LICENSE.txt`.
