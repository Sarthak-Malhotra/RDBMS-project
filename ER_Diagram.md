# Group Travel and Itinerary Management System

## Entity-Relationship (ER) Diagram

```mermaid
erDiagram
    USERS {
        int user_id PK
        string username
        string email
        string password_hash
        datetime created_at
    }
    TRAVEL_GROUPS {
        int group_id PK
        string name
        string description
        int created_by FK
        datetime created_at
    }
    GROUP_MEMBERS {
        int group_id PK, FK
        int user_id PK, FK
        string role
        datetime joined_at
    }
    TRIPS {
        int trip_id PK
        int group_id FK
        string title
        string destination
        date start_date
        date end_date
        string status
    }
    ITINERARIES {
        int itinerary_id PK
        int trip_id FK
        string title
        string description
        datetime start_time
        datetime end_time
        string location
        decimal cost
    }

    USERS ||--o{ TRAVEL_GROUPS : "creates"
    USERS ||--o{ GROUP_MEMBERS : "belongs to"
    TRAVEL_GROUPS ||--o{ GROUP_MEMBERS : "has"
    TRAVEL_GROUPS ||--o{ TRIPS : "organizes"
    TRIPS ||--o{ ITINERARIES : "contains"
```

## Schema Description

1. **USERS**: Stores user accounts.
2. **TRAVEL_GROUPS**: Represents travel groups created by users.
3. **GROUP_MEMBERS**: Junction table mapping users to their respective groups with roles.
4. **TRIPS**: Stores information about specific trips planned by a group.
5. **ITINERARIES**: Represents schedule items/events for a specific trip, and includes the `cost` field for expenses.
