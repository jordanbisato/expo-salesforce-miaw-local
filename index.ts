// Reexport the native module. On web, it will be resolved to ExpoSalesForceMIAWModule.web.ts
// and on native platforms to ExpoSalesForceMIAWModule.ts
export { default } from "./src/ExpoSalesForceMIAWModule";
export * from "./src/ExpoSalesForceMIAW.types";
