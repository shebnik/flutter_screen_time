//
//  TokenManager.swift
//  FlutterScreenTime
//
//  Created by Nikita on 8/21/25.
//

import Foundation
import ManagedSettings

public class TokenManager {
    
    // MARK: – Generic encode/decode for any Codable token
    
    private func encodeToken<T: Codable>(_ token: T) throws -> String {
        let data = try PropertyListEncoder().encode(token)
        return data.base64EncodedString()
    }
    
    private func decodeToken<T: Codable>(_ string: String, as type: T.Type) throws -> T {
        guard let data = Data(base64Encoded: string) else {
            throw NSError(
                domain: "DecodeError", code: -1,
                userInfo: [NSLocalizedDescriptionKey: "Invalid base64"])
        }
        return try PropertyListDecoder().decode(type, from: data)
    }
    
    // MARK: – Type-specific overloads (optional sugar)
    
    func encodeApplicationToken(_ token: ApplicationToken) throws -> String {
        try encodeToken(token)
    }
    
    func decodeApplicationToken(_ string: String) throws -> ApplicationToken {
        try decodeToken(string, as: ApplicationToken.self)
    }
    
    func encodeCategoryToken(_ token: ActivityCategoryToken) throws -> String {
        try encodeToken(token)
    }
    
    func decodeCategoryToken(_ string: String) throws -> ActivityCategoryToken {
        try decodeToken(string, as: ActivityCategoryToken.self)
    }
    
    func encodeWebDomainToken(_ token: WebDomainToken) throws -> String {
        try encodeToken(token)
    }
    
    func decodeWebDomainToken(_ string: String) throws -> WebDomainToken {
        try decodeToken(string, as: WebDomainToken.self)
    }
    
}
