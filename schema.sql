CREATE TABLE users (
    user_id INT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE travel_groups (
    group_id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_by INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE SET NULL
);

CREATE TABLE group_members (
    group_id INT,
    user_id INT,
    role VARCHAR(20) DEFAULT 'MEMBER',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, user_id),
    FOREIGN KEY (group_id) REFERENCES travel_groups(group_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE trips (
    trip_id INT PRIMARY KEY,
    group_id INT,
    title VARCHAR(100) NOT NULL,
    destination VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'PLANNED',
    FOREIGN KEY (group_id) REFERENCES travel_groups(group_id) ON DELETE CASCADE
);

CREATE TABLE itineraries (
    itinerary_id INT PRIMARY KEY,
    trip_id INT,
    title VARCHAR(100) NOT NULL,
    description TEXT,
    start_time TIMESTAMP NOT NULL,
    location VARCHAR(200),
    cost DECIMAL(10, 2) DEFAULT 0.00, 
    FOREIGN KEY (trip_id) REFERENCES trips(trip_id) ON DELETE CASCADE
);
